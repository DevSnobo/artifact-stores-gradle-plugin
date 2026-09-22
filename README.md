# resolution-gradle-plugin

A Gradle plugin that wires a self hosted [Reposilite](https://reposilite.com) instance into a build as a
dependency and plugin repository.

* plugin id: `net.snobo.resolution`
* applied in `settings.gradle.kts` (it is a settings plugin)
* implementation class: `net.snobo.resolution.ReposiliteSettingsPlugin`

Only the Reposilite routes `releases` and `snapshots` are registered by default; the `private` route needs a
token, also for reads, and is opt in.

Credentials come from the Gradle properties `reposilitePublisher` (the **token name**) and
`reposiliteToken` (the **token secret**), so they normally live in `~/.gradle/gradle.properties` and never in
the repository.

## Usage

```kotlin
// settings.gradle.kts
plugins { id("net.snobo.resolution") version "0.1.1" }

reposilite {
    // url = uri("http://127.0.0.1:8080")   // default, or the reposiliteUrl Gradle property
    // includeGroups.add("net.snobo.foundation")
    // useForPlugins = true                 // also use Reposilite to resolve Gradle plugins
    // usePrivate = false                   // also read from /private, which needs a token
    // privateRepository = "private"        // route name of the private repository
    // privateUrl = uri("https://.../private")   // full URL, overrides url + privateRepository
}
```

The extension is created on `Settings`, so `reposilite { }` belongs in `settings.gradle.kts`, not in a
project build script.

## Options

| Option | Default | Meaning |
| --- | --- | --- |
| `url` | `reposiliteUrl` property, else `http://127.0.0.1:8080` | base URL of the instance, without a route |
| `releasesUrl` | `url` + `releasesRepository` | full URL of the releases route, overrides `url` |
| `snapshotsUrl` | `url` + `snapshotsRepository` | full URL of the snapshots route, overrides `url` |
| `privateUrl` | `url` + `privateRepository` | full URL of the private route, overrides `url` |
| `releasesRepository` | `releases` | route name of the releases repository |
| `snapshotsRepository` | `snapshots` | route name of the snapshots repository |
| `privateRepository` | `private` | route name of the private repository |
| `repositoryName` | `Reposilite` | prefix of the created Gradle repositories (`Reposilite-Releases`, ...) |
| `publisher` | `reposilitePublisher` property | Reposilite access token name |
| `token` | `reposiliteToken` property | Reposilite access token secret |
| `includeGroups` | `net.snobo` | groups resolved from Reposilite |
| `useForDependencies` | `true` | register Reposilite as a dependency repository |
| `useForPlugins` | `true` | register Reposilite as a plugin repository (also resolves this plugin itself) |
| `usePrivate` | `false` | also read from the private route |
| `allowInsecure` | `true` | when `false`, a plain HTTP URL fails the build |
| `repositoriesMode` | not set | overrides `dependencyResolutionManagement.repositoriesMode` |

Only the configured `includeGroups` are requested from Reposilite, so other dependencies never contact it.
The private route follows the same rule and is only registered when `usePrivate = true`.
Repositories are created for both `dependencyResolutionManagement.repositories` and
`pluginManagement.repositories` unless the matching `useFor*` flag is disabled.

The plugin warns when a project declares its own repositories, because Gradle then ignores the settings level
repositories (`repositoriesMode = PREFER_PROJECT`). Move those repositories to settings, or set
`reposilite { repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS }`.

## Booting the plugin

The settings plugin cannot add the repository it is loaded from, so `pluginManagement` needs one manual
entry. This is also how a consumer resolves this plugin from Reposilite:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven {
            name = "Reposilite"
            val base = providers.gradleProperty("reposiliteUrl").getOrElse("http://127.0.0.1:8080")
            url = uri("$base/releases")
            isAllowInsecureProtocol = base.startsWith("http://")
            val username = providers.gradleProperty("reposilitePublisher").orNull
            val secret = providers.gradleProperty("reposiliteToken").orNull
            if (username != null && secret != null) {
                credentials {
                    this.username = username
                    password = secret
                }
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins { id("net.snobo.resolution") version "0.1.1" }
```

Use `$base/snapshots` instead of `$base/releases` to resolve a `-SNAPSHOT` version of this plugin. Consumers
that resolve the plugin from Reposilite should run `--refresh-dependencies` after a new snapshot is
published, because Gradle caches `-SNAPSHOT` modules for 24 hours.

## Properties

| Gradle property | Environment variable | Meaning |
| --- | --- | --- |
| `reposiliteUrl` | `REPOSILITE_URL` | base URL, default `http://127.0.0.1:8080` |
| `reposilitePublisher` | `REPOSILITE_PUBLISHER` | access token name (`reposiliteUsername` / `REPOSILITE_USERNAME` also accepted) |
| `reposiliteToken` | `REPOSILITE_TOKEN` | access token secret |

Precedence: `-P` / project property, then `~/.gradle/gradle.properties`, then the environment variable.
Every one of them can also be set through the extension itself (`url`, `publisher`, `token`).

Without credentials the public routes stay readable and an authorized route fails with `401`. The only
protected route this plugin knows is the private route, which it registers only with `usePrivate = true`; it
warns when that route is registered without credentials.

## Release

The version is never hard coded in `build.gradle.kts`; a release passes it on the command line.
`-Pversion=...` is the native Gradle form, and `-Pproject.version=...` is accepted as well, because that
spelling alone does not map onto `Project.version`. Without a version the build is a local build:

```bash
./gradlew -q properties | grep ^version:                           # version: local-SNAPSHOT
./gradlew -Pversion=0.1.1 -q properties | grep ^version:           # version: 0.1.1
```

```bash
./gradlew build                                # check the tree
./gradlew -Pversion=0.1.1 publish              # -> /releases, or /snapshots for a -SNAPSHOT version
git tag release/0.1.1 && git push origin release/0.1.1
```

Release tags always follow `release/X.Y.Z` (or `release/X.Y.Z-RCn`), the pattern the `net.snobo.version` plugin
reads, so a later adoption of that plugin needs no retagging.

Later releases take their version from the `net.snobo.version` plugin instead (see `TODO.md`).

## Development

```bash
./gradlew build                                        # compile, validatePlugins, TestKit tests (Gradle 9.7.1)
REPOSILITE_TEST_GRADLE_VERSION=8.14.3 ./gradlew test --rerun   # same suite against Gradle 8.14.3
```

The functional tests start a local HTTP stub that imitates the Reposilite endpoints (artifact GET for the
`releases`, `snapshots` and `private` routes, with basic authentication), so the tests need no server. The
build itself does: it resolves the plugin that publishes it from Reposilite. With a warm cache,
`./gradlew build --offline` works while the server is down.

### Self dog-fooding

`0.1.0` was published with a hand written `maven-publish` block, because no version of the plugin existed
while that build was configured. From `0.1.1` on this build publishes itself through `net.snobo.publish`
`0.1.0`: `checkReposilitePublishTarget` runs before the first upload, `-SNAPSHOT` goes to `/snapshots` and
everything else to `/releases`.

This plugin cannot be applied to this build before `0.1.2`. A settings plugin needs a published version whose
package matches the working tree, and `0.1.0` still carries the pre-split package
`net.snobo.gradle.plugin`; the manual `pluginManagement` entry in `settings.gradle.kts` is what resolves the
publish plugin, and it stays anyway, because a settings plugin cannot add the repository it is loaded from.

```
./gradlew -Pversion=0.1.1 publish                 # release, default base URL
./gradlew -Pversion=0.1.1-SNAPSHOT publish        # snapshot    -> /snapshots
./gradlew -Pversion=0.1.1 -PreposiliteUrl=https://repo.example.com publish
```

Run `publish` on purpose and nowhere else: it writes to the Reposilite instance named by `reposiliteUrl`.
