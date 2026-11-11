# TODO

Open decisions and follow-ups for `resolution-gradle-plugin` (plugin id `net.snobo.resolution`).

## 0.1.2

- [x] Tag-driven versioning. The build applies `net.snobo.version` 1.0.0, which reads the version from a
      `release/<version>` tag at HEAD and falls back to `local-SNAPSHOT` on every other commit. The old
      `-Pversion` / `-Pproject.version` block is gone; `release/0.1.2` is the test release.
- [x] Release workflow. `.github/workflows/release.yml` runs on `release/**` tags, reuses `ci.yml` through
      `workflow_call`, requires the tag to be an ancestor of `origin/main`, checks with
      `-Pversion.requireRelease=true` against the read-only Reposilite credentials, and then publishes
      `net.snobo.resolution:net.snobo.resolution.gradle.plugin` and `net.snobo:resolution-gradle-plugin`
      with the release token.

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

- [x] 0.1.1 publishes this build with `net.snobo.publish` 0.1.0: the hand written bootstrap block of 0.1.0 is
      gone and `checkReposilitePublishTarget` runs in front of both publications before the first upload.

## Deferred

- [ ] **0.1.2: apply this plugin to its own build.** 0.1.1 publishes itself with `net.snobo.publish` 0.1.0, but
      this plugin cannot be applied before 0.1.2: the published 0.1.0 carries the pre-split package
      `net.snobo.gradle.plugin`, the working tree uses `net.snobo.resolution`, and a settings plugin is loaded
      from its published jar. At 0.1.2 add the manual `pluginManagement` entry for 0.1.1, apply
      `id("net.snobo.resolution")` in `settings.gradle.kts`, move `mavenCentral()` out of the build script and
      set `reposilite.repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS`. Acceptance: a `net.snobo`
      dependency resolves through Reposilite in this build, and the plugin is still resolved from the manual
      entry.

- [ ] HTTPS: currently plain HTTP on loopback (`http://127.0.0.1:8080`). The plugin already handles
      `https` transparently and `reposilite.allowInsecure = false` fails plain HTTP builds. Still
      open: Reposilite `sslEnabled` / reverse proxy, and truststore setup for a self-signed cert.

- [ ] Snapshot cache tuning: consumers of `-SNAPSHOT` artifacts only refresh them after Gradle's 24 hour
      snapshot cache expires. Consider exposing a `refreshSnapshots` flag that sets
      `resolutionStrategy.cacheChangingModulesFor` for the created repositories.

- [ ] Modules that declare their own repositories: the plugin warns when
      `repositoriesMode = PREFER_PROJECT` makes its repositories ineffective. Nothing else implemented.

- [x] CI: GitHub Actions runs `check` and the TestKit suite on Gradle 8.14.3 and 9.7.1
      (`.github/workflows/ci.yml`). The release workflow reuses that file through `workflow_call`.

- [ ] The `test` task ignores `REPOSILITE_TEST_GRADLE_VERSION`, so `REPOSILITE_TEST_GRADLE_VERSION=8.14.3
      ./gradlew test` leaves `:test` UP-TO-DATE after a normal run and silently tests Gradle 9.7.1 instead.
      Declare the environment variable as a task input, or document `./gradlew test --rerun`.

- [ ] Gradle Plugin Portal: not publishing there - the plugin is personal and the portal is public.
      Keep publishing to the Reposilite `releases` route.

- [x] Versioning: from 0.1.2 the build takes its version from release tags (`release/0.1.2`,
      `release/0.2.0`, ...) through `net.snobo.version`; every other commit is `local-SNAPSHOT`.
      `0.1.0` and `0.1.1` were released from the command line.
- [ ] Shared helper code is duplicated. `ReposiliteAccess`, `ReposiliteDefaults`, the property/environment
      lookup and the URL helpers exist in this repository and in `publish-gradle-plugin` (the packages differ
      on purpose - `net.snobo.resolution` here, `net.snobo.publish` there - so both plugins can sit on one
      classpath). If the two copies drift, extract `net.snobo:reposilite-support` and let both plugins depend
      on it.
- [ ] Migrate the consumers. The settings half moved from `net.snobo.reposilite` to `net.snobo.resolution`
      and the publish half from `net.snobo.reposilite-publish` to `net.snobo.publish`. `kmp-base-lib` and
      `festivali` still name the old ids, which only resolve through the old snapshot, so they keep using the
      pre-split plugin until they are updated. Do not touch `kmp-base-lib` without being asked.

- [ ] Second consumer: `festivali` resolves `net.snobo.*` artifacts from Reposilite. Do not change its
      build until the user asks for it.

- [ ] Self-dogfooding history. 0.1.0 was published with a hand written block, because no version of the
      plugin existed while that build was configured; from 0.1.1 the sibling `net.snobo.publish` publishes this
      repository. Applying this plugin to its own build always needs a published version plus the manual
      `pluginManagement` entry, because a settings plugin cannot add the repository it is loaded from - which
      is why the full self-application waits for 0.1.2 (see the Deferred item).
