# TODO

Open decisions and follow-ups for `resolution-gradle-plugin` (plugin id `net.snobo.resolution`).

## Waiting on a decision

- [ ] **Item 6 from the design review (open): token provisioning.**
      Decide whether a scoped, non-manager access token should be created for CI
      (`POST /api/tokens` + `route-add <token> /releases/ r` via the remote console) instead of
      relying on the MANAGER token that currently lives in `~/.gradle/gradle.properties`.
      A manager token works but has full access to every route, the `private` repository and the
      remote console, and it silently bypasses route checks. Options: (a) do nothing and keep using
      the manager token, (b) add an opt-in task that mints a scoped token and prints the secret once,
      (c) document the console commands only.
      The user asked to be reminded about this later.

## Verified

- [x] Private route support. The plugin knows the route name `private` (`privateRepository`) and an
      optional full `privateUrl`. It registers `<name>-Private` only with `usePrivate = true` (default
      false, so nothing changes for existing consumers) and warns when that route is registered without
      credentials. Verified with the TestKit stub: the plugin resolves an artifact that only the private
      route serves and never contacts `/private` without `usePrivate`. Also checked read-only against the
      running instance: `GET /api/maven/details/private` answers `401`, which confirms that reads need a
      token.

- [x] `reposiliteUsername` alias, so existing hand written Reposilite snippets keep working. The alias is
      accepted for the token name, both as a Gradle property and as `REPOSILITE_USERNAME`.

- [x] The plugin registers Reposilite both as a dependency repository
      (`dependencyResolutionManagement.repositories`) and as a plugin repository
      (`pluginManagement.repositories`), each switchable with `useForDependencies` / `useForPlugins`, and
      warns when both are disabled. Verified by the TestKit suite against the stub: an artifact of an
      included group resolves with credentials, a group that is not included never contacts the server.

## Deferred

- [ ] **Version 0.1.1: replace the bootstrap publishing block** in `build.gradle.kts` with the
      `net.snobo.publish` plugin once that plugin has its first release (planned release 0.1.1).
      The hand written block exists only because this repository cannot apply a plugin that is not
      published yet.

- [ ] HTTPS: currently plain HTTP on loopback (`http://127.0.0.1:8080`). The plugin already handles
      `https` transparently and `reposilite.allowInsecure = false` fails plain HTTP builds. Still
      open: Reposilite `sslEnabled` / reverse proxy, and truststore setup for a self-signed cert.

- [ ] Snapshot cache tuning: consumers of `-SNAPSHOT` artifacts only refresh them after Gradle's 24 hour
      snapshot cache expires. Consider exposing a `refreshSnapshots` flag that sets
      `resolutionStrategy.cacheChangingModulesFor` for the created repositories.

- [ ] Modules that declare their own repositories: the plugin warns when
      `repositoriesMode = PREFER_PROJECT` makes its repositories ineffective. Nothing else implemented.

- [ ] CI: GitHub Actions (or other) job that runs the TestKit suite on Gradle 8.14.x and 9.x.

- [ ] The `test` task ignores `REPOSILITE_TEST_GRADLE_VERSION`, so `REPOSILITE_TEST_GRADLE_VERSION=8.14.3
      ./gradlew test` leaves `:test` UP-TO-DATE after a normal run and silently tests Gradle 9.7.1 instead.
      Declare the environment variable as a task input, or document `./gradlew test --rerun`.

- [ ] Gradle Plugin Portal: not publishing there - the plugin is personal and the portal is public.
      Keep publishing to the Reposilite `releases` route.

- [ ] Versioning: pass the version per release with `-Pversion=...` (or `-Pproject.version=...`); the build
      falls back to `local-SNAPSHOT` when no version is given. `0.1.0` is the first release. Once the
      `net.snobo.version` plugin is released, this plugin should take its version from release tags
      (`release/0.2.0`) instead of the command line.

- [ ] Second consumer: `festivali` resolves `net.snobo.*` artifacts from Reposilite. Do not change its
      build until the user asks for it.

- [ ] Self-dogfooding this plugin in its own build: decided against. A settings plugin cannot add the
      repository it is loaded from, so the only options are a published snapshot (a version, a Reposilite or
      warm cache dependency at settings evaluation, a 24 hour `-SNAPSHOT` dead zone) or a jar from a previous
      build. Both make this build depend on the artifact it produces, which is not worth it here. The
      `net.snobo.publish` plugin is dog-fooded from 0.1.1 on, and this plugin is dog-fooded by its consumers
      (`net.snobo.publish`, `net.snobo.version`). If a real end-to-end run inside this repository is wanted
      later, add a small composite build (`pluginManagement { includeBuild("..") }` plus a probe dependency)
      that applies the plugin from source without a version.
