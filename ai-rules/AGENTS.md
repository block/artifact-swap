---
description: "Base respository guidelines"
alwaysApply: true
---

# Repository Guidelines

This repository contains Artifact Swap, a Gradle tooling suite that accelerates configuration for large Kotlin/Java projects by swapping local projects with published artifacts when appropriate. These guidelines summarize how the repo is structured, built, tested, and contributed to, based strictly on files in this repo.

## Project Structure & Module Organization
- Root Gradle build with settings in `settings.gradle.kts`; modules: `:cli`, `:core`, `:gradle-plugin`.
- Convention plugins live in `build-logic/` (notably `conventions`), applied via `pluginManagement.includeBuild("build-logic")`.
- Source code:
  - CLI: `cli/src/main/kotlin/**` with tests in `cli/src/test/kotlin/**`.
  - Core library: `core/src/main/kotlin/**` with tests and `testFixtures` in `core/src/test*/kotlin/**`.
  - Gradle plugins: `gradle-plugin/src/main/kotlin/**` (plus one Groovy source in `.../src/main/groovy/**`).

## Build, Test, and Development Commands
- Local quickstart: `./gradlew build` (per CONTRIBUTING.md).
- Formatting: `./gradlew ktfmtCheck` and `./gradlew ktfmtFormat` (configured via ChecksPlugin).
- Tests: `./gradlew test` (JUnit Platform); GitHub Actions also runs `./gradlew buildHealth` and `./gradlew lint`.
- CLI E2E (as in CI):
  1) `./gradlew :cli:publishToMavenLocal`
  2) `VERSION=$(grep "^version=" gradle.properties | cut -d'=' -f2)`
  3) `cd ~/.m2/repository/xyz/block/artifactswap/cli/$VERSION && unzip -q cli-$VERSION.zip`
  4) `./artifactswap-$VERSION/bin/artifactswap --help`
- Publishing: `./gradlew publishAllPublicationsToMavenCentralRepository` with credentials set via Gradle properties (see GitHub workflow and `PublishPlugin`).

## Coding Style & Naming Conventions
- Kotlin code with standard 2-space indentation (inferred from sources). Formatting enforced by ktfmt via `build-logic/conventions/.../ChecksPlugin.kt`.
- Package naming uses `xyz.block.artifactswap.*`. Gradle plugin IDs: `xyz.block.artifactswap`, `xyz.block.artifactswap.settings`, `xyz.block.artifactswap.publish`, and `xyz.block.artifactswap.groovy-override`.
- Linting: Android Lint plugin enabled for Gradle checks (applies to JVM modules for Gradle config lint rules).
- When generating Kotlin code where you are importing a class/function from another package, make sure you reference that class/function using an import statement instead of the full-qualified class name.

## Testing Guidelines
- Frameworks: JUnit 5 (JUnit BOM with `junit-jupiter-api`), Mockito, kotlinx-coroutines-test.
- Run all tests: `./gradlew test`. CI uses `test` plus `buildHealth` and `lint`.
- Test fixtures available in `core` via `java-test-fixtures` and `testFixtures(...)` dependencies.

## Commit & Pull Request Guidelines
- History indicates Conventional-Commit-like prefixes and PR-number references (e.g., `Fix ... (#70)`, dependency updates via Renovate).
- Target branch for CI is `main`; markdown-only changes are ignored by CI (`paths-ignore: '*.md'`).
- For releases to Maven Central, the `Publish` workflow triggers after `Test` completes on `main` and only for `block/artifact-swap`.

## Notes & TODOs
- .gitignore marks typical Gradle outputs (`**/build/`, `.gradle/`) and IDE files. No explicit code coverage thresholds are defined; CI does not enforce coverage.
- TODO: Verify any additional wrapper scripts or developer tasks beyond those in workflows and `CONTRIBUTING.md`.
