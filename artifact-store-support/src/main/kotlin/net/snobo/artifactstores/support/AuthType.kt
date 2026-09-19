package net.snobo.artifactstores.support

/** How requests to an artifact store authenticate. */
enum class AuthType {
    /** Credentials from the store username and password. */
    BASIC,

    /** Credentials from the store header name and value. */
    HEADER,

    /** No credentials. */
    NONE,
}
