## 0.1.11
- Add GradleBuildFileInspectionSuppressor to hide IDE warnings about unknown project accessors caused by swapping their project references
- Update spotlight
- Require spotlight be present, use compileOnly dependency on it

## 0.1.10
- Add IDE plugin to support navigation in projects with swapped artifacts
- Require local protos to be explicitly enabled
- Add Develocity integration

## 0.1.9
- More aggressive timeouts and retries in artifactory client
- Update spotlight version

## 0.1.8
- Move publish plugin to a new artifact to support projects with classpath issues
- Add SocketTimeoutException retries with a backoff

## 0.1.7
- Force configure spotlight to work around Gradle nested callback issue
- Fix finding project to swap from artifact id (allprojects scope issue)
- Improve ProjectAccessorDelegate to handle missing property and method calls

## 0.1.6
- Add DSL handler for enabling/disabling artifact swapping

## 0.1.5
- Add support for KTS buildscripts with project() override handling
- Add support for generated project accessors in Groovy buildscripts
- Fix registration of BOM service
- Fix origin/ prefix being applied incorrectly when comparing shared commits
- Read artifact group from config in LocalArtifactRepository
- Add default logger for eventstream

## 0.1.4
- Add module selection via Spotlight and/or `ArtifactSwapModuleSelector`
- Improve handling of android library publishing by considering flavors
- Make branch name for publishing/reading BOM versions a configuration option
- Add support for reading artifactory password from token file
- Migrate publish configs from `square.*` to `artifactswap.*` naming
- Add replacement property names for publish plugin
- Support publishing to localhost without https

## 0.1.3
- Update plugins to support module selection based on present artifacts
- Additional cleanup of configuration names from old internal values

## 0.1.2
* Fix secondary repo configuration value was unused

## 0.1.1
* Fix publication of android libraries

## 0.1.0
* Prototype release
