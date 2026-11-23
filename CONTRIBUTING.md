# Contributing

Contributions are welcome. This repository is a small Gradle settings plugin; the rules below keep
the build reproducible and every change reviewable.

## Start here

- Use the Gradle wrapper (`./gradlew`). The build requires JDK 21; the published plugin targets
  Java 17.
- For a bug or a feature request, open an issue using the template.
- Report security problems privately as described in [SECURITY.md](SECURITY.md), never in a public
  issue.
- By contributing, you agree that your changes are licensed under the
  [Apache License 2.0](LICENSE).

## Build and test

```bash
./gradlew build
./gradlew check
ARTIFACT_STORE_TEST_GRADLE_VERSION=8.14.7 ./gradlew test
ARTIFACT_STORE_TEST_GRADLE_VERSION=9.9.1 ./gradlew test
```

`check` runs ktlint, plugin validation and the TestKit suite. Functional tests start an in-process
HTTP stub, so no artifact server or credentials are needed. See the
[compatibility matrix](README.md#compatibility) for the tested Gradle, JDK and OS combinations.

## Dogfooded plugin version

The build applies the last published `net.snobo.artifact-stores` in `settings.gradle.kts`. Bump that
version after every release. CI compares it with the latest stable version on the Gradle Plugin Portal
and fails when the build is behind. A release pull request that intentionally applies the version it
is about to publish can set the repository variable `DOGFOOD_VERSION_OVERRIDE` to that version.

## Public API

The public API is dumped under `api/` and checked by the `API compatibility` CI job. Binary
compatibility validation is not Isolated Projects compatible yet, so `apiCheck` is not part of
`check`. After a public API change, regenerate the dumps and commit them:

```bash
./gradlew apiDump -Dorg.gradle.isolated-projects=false
```

## Change checklist

- Keep one logical change per pull request and use an imperative commit subject such as
  `fix: ...`.
- Add or update tests for behavior changes.
- Run `./gradlew check` before pushing.
- If you change a dependency or a build plugin, refresh the lock state and verification metadata,
  and commit both with the change:
  `./gradlew resolveAndLockAll --write-locks --write-verification-metadata sha256`.
- Update `README.md` when you change the DSL, a default, or other user-visible behavior.
- Add verification as a Gradle task wired into `check`, not as a standalone script.
- Do not commit credentials, private host names, tokens or local absolute paths. Use placeholders
  in examples.

## Style

- ktlint enforces the shared `.editorconfig` (ktlint_official, 120 columns, 4-space indent,
  trailing commas). `./gradlew ktlintFormat` rewrites the sources.
- Keep comments only where they explain a decision the code cannot.

## Pull requests

- CI must pass. The maintainer reviews every change.
- Release tags (`release/X.Y.Z`) belong to the maintainer; do not add or move them in a pull
  request.
- See [README.md](README.md#releasing) for the release flow.
