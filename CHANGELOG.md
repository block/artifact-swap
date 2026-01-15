## Unreleased
- Add SocketTimeoutException retries with a backoff

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
