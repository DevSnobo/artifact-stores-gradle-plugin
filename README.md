# Artifact stores Gradle plugin

![Isolated Projects compatible](https://img.shields.io/badge/Isolated%20Projects-compatible-brightgreen)

A Gradle settings plugin that registers Maven artifact stores as dependency and plugin repositories.
One block in `settings.gradle.kts` configures Reposilite, Nexus, Artifactory or any generic Maven repository,
including its URLs, credentials and repository routes.

* Plugin id: `net.snobo.artifact-stores`
* Applied in `settings.gradle.kts`
* Requires Gradle 8.14.7 or newer and Java 17 or newer
* Compatible with the configuration cache and Isolated Projects
* Licensed under Apache-2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE))

## What it does

The plugin reads its configuration while Gradle evaluates settings. It then adds Maven repositories to
`dependencyResolutionManagement.repositories` (for dependencies) and `pluginManagement.repositories` (for
Gradle plugins), before any project is configured. Both kinds of resolution can therefore use the same store.

Each store is created by a preset function: `reposilite`, `nexus`, `artifactory` or `generic`. The preset
applies the default routes, authentication mode and base URL of that product. A configuration block overrides
anything that differs.

## Install

```kotlin
// settings.gradle.kts
plugins { id("net.snobo.artifact-stores") version "..." }
```

Replace `"..."` with a published version. The plugin is a settings plugin, so the `plugins` block and the
configuration belong in `settings.gradle.kts`.

## Quick starts

### Reposilite

```kotlin
// settings.gradle.kts
plugins { id("net.snobo.artifact-stores") version "..." }

artifactStores {
    reposilite("oss") {
        includeGroups.add("com.example")
    }
}
```

The preset points at `http://127.0.0.1:8080` by default and registers
`http://127.0.0.1:8080/releases` and `http://127.0.0.1:8080/snapshots`. Set `url` for another instance:

```kotlin
reposilite("oss") {
    url = uri("https://repo.example.com")
    includeGroups.add("com.example")
    includeGroups.add("com.example.plugins")
}
```

Reposilite uses basic authentication with an access token name and secret. The preset routes are `releases`
and `snapshots`. Set `releasesRepository` and `snapshotsRepository` to use other route segments; the plugin
appends them to `url` when it builds the repository URLs.

### Nexus

```kotlin
artifactStores {
    nexus("corp") {
        url = uri("https://nexus.example.com")
        includeGroups.add("com.example")
    }
}
```

A Nexus base URL is optional. When you set `url`, the preset appends `repository/maven-releases` and
`repository/maven-snapshots`, so the example uses
`https://nexus.example.com/repository/maven-releases` and
`https://nexus.example.com/repository/maven-snapshots`. With full `releasesUrl` and `snapshotsUrl` values and
no `url`, the plugin registers those URLs as they are. Nexus uses basic authentication.

### Artifactory

```kotlin
artifactStores {
    artifactory("mirror") {
        url = uri("https://artifactory.example.com")
        includeGroups.add("com.example")
    }
}
```

An Artifactory base URL is optional. When you set `url`, the preset appends `artifactory/libs-release` and
`artifactory/libs-snapshot`. With full `releasesUrl` and `snapshotsUrl` values and no `url`, the plugin
registers those URLs as they are. Artifactory uses basic authentication. If your repository keys differ,
override `releasesRepository` and `snapshotsRepository`; the plugin appends the overridden route to `url`.

### Generic

A generic store has no route defaults. Give it the full URL of the repository:

```kotlin
artifactStores {
    generic("central") {
        releasesUrl = uri("https://repo1.maven.org/maven2")
        includeGroups.add("com.example")
    }
}
```

A generic store defaults to no authentication and registers a single repository, because `snapshotsUrl`
falls back to `releasesUrl`. It also works with only `url`, which the plugin then registers as the single
repository. Set `snapshotsUrl` to register a second repository for snapshots. Header
authentication is available when the server expects an HTTP header instead of basic credentials:

```kotlin
import net.snobo.artifactstores.AuthType

artifactStores {
    generic("mirror") {
        releasesUrl = uri("https://mirror.example.com/maven2")
        auth.set(AuthType.HEADER)
        // headerName and headerValue come from artifactStore.mirror.headerName and
        // artifactStore.mirror.headerValue, or the matching environment variables.
    }
}
```

## Multi-store example

Stores are tried in declaration order. Use `includeGroups` to keep each store responsible for its own groups.

```kotlin
import org.gradle.api.initialization.resolve.RepositoriesMode

// settings.gradle.kts
plugins { id("net.snobo.artifact-stores") version "..." }

artifactStores {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    refreshChangingModules.set(true)

    reposilite("internal") {
        url = uri("https://repo.example.com")
        includeGroups.add("com.example")
    }
    nexus("partner") {
        url = uri("https://nexus.partner.example.com")
        includeGroups.add("com.partner")
    }
    generic("central") {
        releasesUrl = uri("https://repo1.maven.org/maven2")
        includeGroups.add("org.apache")
    }
}
```

`repositoriesMode` overrides `dependencyResolutionManagement.repositoriesMode`. When projects declare their
own repositories and the mode is `PREFER_PROJECT`, Gradle ignores the settings repositories; the plugin logs one
warning per such project, naming the project path. `FAIL_ON_PROJECT_REPOS` turns that situation into a build
failure.

`refreshChangingModules = true` stops Gradle from honoring the changing-module cache: every configuration of
every project gets `cacheChangingModulesFor(0, "seconds")`, applied per project through
`gradle.lifecycle.beforeProject`. Gradle has no per-repository cache policy, so the switch is build-wide and
also affects dependencies that do not come from an artifact store.

## `ArtifactStore` reference

Every preset function returns an `ArtifactStore` and accepts a configuration block. All properties are Gradle
`Property`/`ListProperty` values, so use `set(...)`, `add(...)` or Kotlin DSL assignment as usual.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `type` | `ArtifactStoreType` | set by the preset | Store kind: `REPOSILITE`, `NEXUS`, `ARTIFACTORY` or `GENERIC`. Normally not set by hand. |
| `url` | `URI` | preset default URL, if any | Base URL of the store, without a route segment. Optional when full repository URLs are set. |
| `releasesUrl` | `URI` | unset | Full URL of the releases repository. Overrides `url` plus `releasesRepository`. |
| `snapshotsUrl` | `URI` | unset; generic falls back to `releasesUrl` | Full URL of the snapshots repository. Overrides `url` plus `snapshotsRepository`. |
| `releasesRepository` | `String` | preset route | Route segment appended to `url` for releases. Also settable as `artifactStore.<name>.releasesRepository` or `ARTIFACT_STORE_<NAME>_RELEASES_REPOSITORY`. |
| `snapshotsRepository` | `String` | preset route | Route segment appended to `url` for snapshots. Also settable as `artifactStore.<name>.snapshotsRepository` or `ARTIFACT_STORE_<NAME>_SNAPSHOTS_REPOSITORY`. |
| `repositoryName` | `String` | the instance name | Prefix of the created Gradle repositories. |
| `username` | `String` | `artifactStore.<name>.username` or `ARTIFACT_STORE_<NAME>_USERNAME` | User name for basic authentication. |
| `password` | `String` | `artifactStore.<name>.password` or `ARTIFACT_STORE_<NAME>_PASSWORD` | Password or token secret for basic authentication. Never logged. |
| `auth` | `AuthType` | preset authentication mode | `BASIC`, `HEADER` or `NONE`. |
| `headerName` | `String` | `artifactStore.<name>.headerName` or `ARTIFACT_STORE_<NAME>_HEADER_NAME` | HTTP header name for header authentication. |
| `headerValue` | `String` | `artifactStore.<name>.headerValue` or `ARTIFACT_STORE_<NAME>_HEADER_VALUE` | HTTP header value for header authentication. Never logged. |
| `includeGroups` | `List<String>` | empty (no content filter) | Groups served by the store. Each entry also includes its subgroups. When the list is empty, the repository accepts any group. |
| `allowInsecure` | `Boolean` | `true` | Opt in to plain HTTP. When `false`, a resolved plain HTTP URL fails the build. |
| `forDependencies` | `Boolean` | `true` | Register the store in `dependencyResolutionManagement.repositories`. |
| `forPlugins` | `Boolean` | `true` | Register the store in `pluginManagement.repositories`. |

The preset functions also accept a name without a configuration block, for example `reposilite("oss")`. A
store with both `forDependencies` and `forPlugins` set to `false` registers no repository, logs a warning, and
skips URL and credential validation.

### Created repository names

A store whose releases and snapshots URLs differ creates two Gradle repositories:
`<repositoryName>-Releases` and `<repositoryName>-Snapshots`. A store whose two URLs are identical creates one
repository named `<repositoryName>`. `repositoryName` defaults to the instance name. Repository names must be
unique within each repository handler. Two stores may share a name when one is dependency-only and the other
is plugin-only, because they register in different handlers. A collision within
`dependencyResolutionManagement.repositories` or `pluginManagement.repositories` fails the build and asks for
a different `repositoryName`.

## Extension reference

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `repositoriesMode` | `RepositoriesMode` | unset | Overrides `dependencyResolutionManagement.repositoriesMode`. |
| `refreshChangingModules` | `Boolean` | `false` | Refresh changing modules on every build by setting `cacheChangingModulesFor(0, "seconds")` on every configuration of every project through `gradle.lifecycle.beforeProject`. Build-wide. |

## Presets

| Preset | Default base URL | Release route | Snapshot route | Default auth | `url` required | Snapshots share releases |
| --- | --- | --- | --- | --- | --- | --- |
| `reposilite("...")` | `http://127.0.0.1:8080` | `releases` | `snapshots` | `BASIC` | no | no |
| `nexus("...")` | none | `repository/maven-releases` | `repository/maven-snapshots` | `BASIC` | no, but `url` or both full URLs | no |
| `artifactory("...")` | none | `artifactory/libs-release` | `artifactory/libs-snapshot` | `BASIC` | no, but `url` or both full URLs | no |
| `generic("...")` | none | none; `url` is used as is | none; falls back to releases | `NONE` | no, but `url` or `releasesUrl` | yes |

Pass the base URL of the server to `url`; the preset appends its own routes. The `releasesRepository` and
`snapshotsRepository` values replace those preset routes. When you set `releasesUrl` and `snapshotsUrl`
instead, the full URLs are used without a base URL, for every preset. A store with neither `url` nor
`releasesUrl` fails with a message that names the store and the `url`, `releasesUrl` and `releasesRepository`
sources it looked for.

## Credentials

Credentials and URLs can come from the DSL, from Gradle properties or from environment variables.

| Source | Form |
| --- | --- |
| Gradle property | `artifactStore.<name>.<field>` |
| Environment variable | `ARTIFACT_STORE_<NAME>_<FIELD>` |

`<name>` is the name given to the preset, for example `oss` in `reposilite("oss")`. Environment variable names
are normalized: the store name is upper-cased and every character outside `A-Z` and `0-9` becomes `_`. For
example the store `internal-snapshots` uses `ARTIFACT_STORE_INTERNAL_SNAPSHOTS_USERNAME`. Two store names that
normalize to the same prefix, such as `a-b` and `a_b`, are rejected.

The available fields are `url`, `releasesUrl`, `snapshotsUrl`, `releasesRepository`, `snapshotsRepository`,
`username`, `password`, `headerName` and `headerValue`. Their Gradle property and environment variable
spellings follow the same pattern:

```properties
# ~/.gradle/gradle.properties, or a file that is not committed
artifactStore.oss.username=token-name
artifactStore.oss.password=token-secret
artifactStore.oss.url=https://repo.example.com
```

```bash
export ARTIFACT_STORE_OSS_USERNAME=token-name
export ARTIFACT_STORE_OSS_PASSWORD=token-secret
```

Precedence, from highest to lowest:

1. A value assigned in the store's DSL block.
2. The Gradle property `artifactStore.<name>.<field>` (`-P`, `gradle.properties`, or another Gradle property
   source).
3. The environment variable `ARTIFACT_STORE_<NAME>_<FIELD>`.
4. The preset default (for example the Reposilite base URL).

Basic authentication counts as configured only when both `username` and `password` are present. One value
without the other fails the build. With neither, the plugin logs a warning and registers the repository without
credentials, so public reads may work and protected reads fail with `401`. For header authentication the same
rules apply to `headerName` and `headerValue`.

### CI example

```yaml
- name: Build
  run: ./gradlew build
  env:
    ARTIFACT_STORE_INTERNAL_USERNAME: ${{ secrets.ARTIFACT_STORE_USERNAME }}
    ARTIFACT_STORE_INTERNAL_PASSWORD: ${{ secrets.ARTIFACT_STORE_PASSWORD }}
```

## Security notes

* `allowInsecure` defaults to `true`. The plugin warns about every plain HTTP URL except `127.0.0.1`. Set
  `allowInsecure = false` to reject plain HTTP; the build then fails with the store name and the offending URL.
* HTTP sends credentials unencrypted. Use HTTPS for anything except a local store.
* `includeGroups` limits which groups a store is asked for, and with them which requests carry its
  credentials. An empty list means no filter: the repository is used for every group that reaches it. Set it
  for stores that should only serve their own artifacts.
* A protected store needs credentials for metadata and artifact reads alike. Without them Gradle receives
  `401` responses, and the plugin logs a warning when basic or header authentication has no values.
* Keep credentials in `~/.gradle/gradle.properties` or CI secrets, not in a committed file.
* Credentials are not logged. Messages refer to the property and environment variable names, not their values.

## How repositories are registered

The plugin is a `Settings` plugin. It reads the configuration in `settingsEvaluated`, creates the repositories,
and adds them to both the settings dependency repositories and the plugin-management repositories. This happens
before Gradle configures any project, so project dependencies and project plugins both resolve through the
stores. It also means the `artifactStores` block belongs in `settings.gradle.kts`.

## Releasing

Release tags use the form `release/X.Y.Z`. The release workflow requires the tag to be an ancestor of
`origin/main`, derives the version from the tag, publishes to Reposilite when `reposiliteUrl` and the
publish token are set, and attests the jars when the repository is public. It then creates a GitHub
Release for the tag with generated notes and uploads the jars built for that version.

To require signed tags, commit the maintainer's public key under `.github/keys/*.asc` and set the
repository variable `REQUIRE_SIGNED_TAGS` to `true`. The release job then fails on an unsigned tag
before it publishes anything.

## Development

```bash
./gradlew build                              # compile, ktlint, plugin validation and the TestKit suite
./gradlew check                              # the verification tasks alone
./gradlew publishToMavenLocal                # publish the plugin to the local Maven repository
ARTIFACT_STORE_TEST_GRADLE_VERSION=8.14.7 ./gradlew test
ARTIFACT_STORE_TEST_GRADLE_VERSION=9.9.1 ./gradlew test
```

`check` runs the functional tests through Gradle TestKit. The Gradle version of the outer build is used unless
`ARTIFACT_STORE_TEST_GRADLE_VERSION` names another release. CI runs the suite on Gradle 8.14.7 and 9.9.1. The
functional tests start an in-process HTTP stub, so no artifact server is needed.

A clean clone needs only public repositories: the build has no private plugin dependency. Every build that is
not passed `-Pversion=<release>` is `local-SNAPSHOT`. The release workflow derives the version from the
`release/X.Y.Z` tag and publishes only when `reposiliteUrl` and the publish token are set.

Dependency versions are locked in `gradle.lockfile` and `artifact-store-support/gradle.lockfile`, and
their bytes are verified against `gradle/verification-metadata.xml`. After changing a dependency or a
build plugin, refresh both controls and commit them with the change:

```bash
./gradlew resolveAndLockAll --write-locks --write-verification-metadata sha256
```

The verification file covers the buildscript and plugin classpaths plus every resolvable project
configuration, including the TestKit fixtures and ktlint.

The published plugin jar is self-contained: the `artifact-store-support` classes are shaded into it, and the
POM does not declare that support project as a dependency.

## License

Apache License 2.0. See [LICENSE](LICENSE) for the full text and [NOTICE](NOTICE) for the copyright notice.
