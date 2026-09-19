package net.snobo.gradle.plugin

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/** Minimal Reposilite stand-in: records requests and serves artifacts the settings plugin resolves. */
internal class ReposiliteStub(
    private val tokenName: String = "net-snobo-publisher",
    private val tokenSecret: String = "stub-secret",
    /** Routes that hand out artifacts. Other routes answer 404, like an empty repository. */
    private val artifactRoutes: Set<String> = setOf("releases", "snapshots", "private"),
) {

    data class Recorded(val method: String, val path: String, val authorization: String?)

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests: MutableList<Recorded> = CopyOnWriteArrayList()

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun start(): ReposiliteStub {
        server.createContext("/") { exchange -> handle(exchange) }
        server.executor = null
        server.start()
        return this
    }

    fun stop() = server.stop(0)

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val authorization = exchange.requestHeaders.getFirst("Authorization")
        requests.add(Recorded(exchange.requestMethod, path, authorization))

        when {
            path.endsWith("/maven-metadata.xml") -> notFound(exchange)

            path.endsWith(".pom") && servesArtifacts(path) -> respond(exchange, 200, pom(), "application/xml")
            path.endsWith(".pom") -> notFound(exchange)

            path.endsWith(".sha1") && servesArtifacts(path) -> {
                val target = path.removeSuffix(".sha1")
                val content = if (target.endsWith(".pom")) pom().toByteArray() else "stub artifact".toByteArray()
                respond(exchange, 200, sha1(content), "text/plain")
            }
            path.endsWith(".sha1") -> notFound(exchange)

            path.endsWith(".md5") || path.endsWith(".sha256") || path.endsWith(".sha512") -> notFound(exchange)

            path.endsWith(".module") -> notFound(exchange)

            path.endsWith(".jar") && servesArtifacts(path) -> respond(exchange, 200, "stub artifact", "application/java-archive")
            path.endsWith(".jar") -> notFound(exchange)

            else -> notFound(exchange)
        }
    }

    /** whether the route in front of an artifact path hands out artifacts, for example `private`. */
    private fun servesArtifacts(path: String): Boolean = path.trim('/').substringBefore('/') in artifactRoutes

    private fun notFound(exchange: HttpExchange) =
        respond(exchange, 404, """{"status":404,"message":"File not found"}""")

    private fun pom() = """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>net.snobo.probe</groupId>
          <artifactId>probe</artifactId>
          <version>1.0</version>
          <packaging>jar</packaging>
        </project>
    """.trimIndent()

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun respond(exchange: HttpExchange, status: Int, body: String, contentType: String = "application/json") {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", contentType)
        if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
            exchange.sendResponseHeaders(status, -1)
            exchange.responseBody.use { }
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
