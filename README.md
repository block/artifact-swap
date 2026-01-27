# Artifact Swap
## Overview

Artifact Swap is a Gradle and IntelliJ plugin that dramatically accelerates IDE sync times for large Gradle builds. It works by swapping local Gradle projects with eagerly fetched, pre-compiled artifacts when appropriate, minimizing the number of projects Gradle needs to configure. This can result in a 50% reduction in sync times for large projects. See our [blog post][shrinking-elephants] and [this Droidcon NYC talk][airbnb-droidcon-talk] for more details.

## How It Works
Artifact Swap operates through three main components:

1. **Gradle Plugin**: Analyzes your project structure, determines which modules to include based on local changes and dependencies, and configures Gradle to use artifacts instead of project references
2. **CLI Tool**: Downloads artifacts from your artifact repository and manages a local Bill of Materials (BOM) that tracks artifact versions
3. **IDE Plugin**: Helps navigate between project source code and the swapped artifacts

## Prerequisites
- A Gradle build using [Spotlight][spotlight] and at least Gradle 8.12
- Java 17 or higher for running the CLI tool
- [Spotlight IDE Plugin][spotlight-ide-plugin]

## Getting Started
### 1. Apply the Plugin
In your `settings.gradle(.kts)`, apply the Artifact Swap settings plugin:

```kotlin
pluginManagement {
  repositories {
    mavenCentral()
  }

  plugins {
    id("xyz.block.artifactswap.settings") version "<latest>"
  }
}

plugins {
  id("xyz.block.artifactswap.settings")
}
```

### 2. Configure Required Properties
Create or update `gradle.properties` in your project root with the required configuration:

```properties
# Primary repository name in your artifact management system
artifactswap.primaryRepositoryName=my-company-artifacts

# Maven group ID for your internal artifacts
artifactswap.primaryArtifactsMavenGroup=com.mycompany.artifactswap.artifacts

# Git branch name where BOM versions are published
artifactswap.bomSourceBranchName=origin/artifact-swap-green-main

# Base URL for your Artifactory instance
artifactswap.artifactoryBaseUrl=https://artifactory.mycompany.com

# Token file name for Artifactory authentication (used in CI)
artifactswap.artifactoryPublisherTokenFileName=artifactory-token.txt
```

### 3. Optional: Configure the Extension
You can programmatically control Artifact Swap behavior in `settings.gradle(.kts)`:

```kotlin
artifactSwap {
  // Enable/disable artifact swap
  enabled(true)

  // Or use a provider for dynamic configuration
  enabled(providers.gradleProperty("artifactswap.enabled").map { it.toBoolean() })
}
```

### 4. Install the CLI Tool
The CLI tool manages artifact downloads and BOM versions. Download and install it:

#### Option A: From Published Release
```bash
# Download the CLI distribution
VERSION="<latest>"
curl -L "https://repo.maven.apache.org/maven2/xyz/block/artifactswap/cli/$VERSION/cli-$VERSION.zip" -o artifactswap.zip

# Extract to your tools directory
mkdir -p tools/artifactswap
unzip artifactswap.zip -d tools/artifactswap
```

#### Option B: Build From Source
```bash
# Clone and build
git clone https://github.com/block/artifact-swap.git
cd artifact-swap
./gradlew :cli:publishToMavenLocal

# Extract from maven local
VERSION=$(grep "^version=" gradle.properties | cut -d'=' -f2)
cd ~/.m2/repository/xyz/block/artifactswap/cli/$VERSION
unzip cli-$VERSION.zip -d ~/Development/your-project/tools/artifactswap
```

#### Create a Wrapper Script
Create a script at `scripts/artifact-swap/download-artifacts.sh`:

```bash
#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset

git_root=$(git rev-parse --show-toplevel)
tools_root="$git_root/tools"
branch=artifact-swap-green-main  # Your BOM branch name

# Attempt to fetch BOM branch
if ! git fetch origin $branch --quiet 2>/dev/null; then
  git update-ref -d refs/remotes/origin/$branch 2>/dev/null || true
  git fetch origin $branch --quiet 2>/dev/null || true
fi

# Ensure the reference exists after fetch
if ! git show-ref --verify --quiet refs/remotes/origin/$branch; then
  git update-ref refs/remotes/origin/$branch FETCH_HEAD
fi

echo "Syncing artifacts for Artifact Swap"
"$tools_root/artifactswap/artifactswap-$VERSION/bin/artifactswap" download-artifacts "$@"
```

Make it executable:

```bash
chmod +x scripts/artifact-swap/download-artifacts.sh
```

### 5. Set Up Git Hooks
To automatically download artifacts when switching branches, configure Git hooks:

```bash
# In your project root
cat > .git/hooks/post-checkout << 'EOF'
#!/bin/bash
./scripts/artifact-swap/download-artifacts.sh > /dev/null 2>&1 & disown
EOF
# Or just add the above command to an existing post-checkout hook

chmod +x .git/hooks/post-checkout
```

## Usage
### Controlling Which Modules Are Included
Use the [Spotlight IDE Plugin][spotlight-ide-plugin] to choose the projects you want to load in the IDE.

Artifact Swap automatically determines which additional modules to include based on:

1. **Git Changes**: Modules that have changes (both committed or not) compared to the branch specified by the `artifactswap.bomSourceBranchName` property
2. **Missing artifacts**: Modules without available published artifacts or ones that have not been downloaded
3. **Always included modules**: Some projects cannot be swapped due to various limitations and can be listed in `gradle/artifact-swap-always-keep.txt`

Only projects that are transitive dependencies of your requested modules are analyzed for inclusion based on the additional critera above.

### Manual Artifact Download
Download artifacts for the current branch:

```bash
./scripts/artifact-swap/download-artifacts.sh --logging debug
```


### Disabling Artifact Swap
Disable via `gradle.properties`:

```properties
artifactswap.enabled=false
```

## How the BOM (Bill of Materials) Works
Artifact Swap uses a BOM to track which version of each module should be used:

1. **BOM Generation**: Your CI pipeline publishes a BOM file to a specific Git branch (`artifact-swap-green-main`) containing module versions
2. **BOM Resolution**: The plugin finds the most recent commit on that branch that's also in your current branch's history
3. **Artifact Download**: The CLI downloads all artifacts for that BOM version from Artifactory to Maven Local
4. **Dependency Resolution**: Gradle resolves swapped dependencies from Maven Local

## CI Publishing Configuration
### How It Works
1. **Hashing**: The `hashing` command computes content hashes for all modules to create deterministic version identifiers
2. **Task Finding**: The `task-finder` command analyzes the build to determine which publish tasks are available
3. **Artifact Checking**: The `artifact-checker` command queries Artifactory to filter out tasks for artifacts that already exist
4. **Task Running**: The `task-runner` command executes only the necessary publish tasks
5. **BOM Publishing**: The `bom-publisher` command creates and publishes a BOM file mapping each module to its content-based version
6. **Branch Update**: The BOM tracking branch (e.g., `artifact-swap-green-main`) is updated to point to the current commit, allowing developers to discover the latest available BOM

To enable Artifact Swap in your CI pipeline, you need to set up two separate publishing steps: one for artifacts and one for the BOM.

### Prerequisites
#### 1. Apply the Publish Plugin
The publish plugin configures Maven publishing for your modules. Choose one of these options:

**Option A: Auto-apply via Settings Plugin (Recommended)**

Enable the `artifactswap.publishingEnabled` property in `gradle.properties` and the settings plugin will automatically apply the publish plugin to all library projects:

```properties
artifactswap.publishingEnabled=true
```

The plugin is automatically applied to projects with the `java`, `java-library`, `org.jetbrains.kotlin.jvm`, or `com.android.library` plugins.

> [!IMPORTANT]
> All your build plugins must be loaded in the settings classpath for this to work

**Option B: Manual Application via Build Logic**

If you prefer more control, apply the publish plugin manually through your convention plugins (in `build-logic/` or similar):

```kotlin
// build-logic build.gradle(.kts)
dependencies {
  implementation("xyz.block.artifactswap:gradle-publish-plugin:<latest>")
}
```

```kotlin
// Your build-logic library plugin(s)
class MyLibraryPlugin : Plugin<Project> {
  override fun apply(target: Project) = target.run {
    // ...
    pluginManager.apply(ArtifactSwapProjectPublishPlugin::class.java)
  }
}
```

This approach allows you to selectively apply publishing to specific modules or customize the configuration. It also may help in situations where you have classpath issues in your build that prevent the auto-apply from working.

#### 2. Configure Publishing Properties

Add these properties to your `gradle.properties`:

```properties
# Artifact repository URL for publishing
artifactswap.artifactRepo.url=https://artifactory.mycompany.com/artifactory/my-repo

# Credentials for publishing (typically set via environment variables in CI)
# artifactswap.artifactRepo.username=publisher
# artifactswap.artifactRepo.password=secret
```

**Note**: Credentials can also be provided via a token file. Set the `SECRETS_PATH` environment variable to a directory containing the token file specified by `artifactswap.artifactoryPublisherTokenFileName` (defaults to `artifactory-token.txt`).

### Step 1: Publishing Artifacts
The artifact publishing process uses the CLI tool to intelligently publish only changed modules. This should be run in your main branch builds:

```bash
#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset

# Allocate memory to prevent OOM crashes
export ARTIFACTSWAP_TOOL_OPTS="-Xmx3072M"
# Configure your CI pipeline to save this artifact for use in the BOM publishing stage
export ARTIFACTSWAP_HASHING_FILE=artifactswapHashes/artifactswapHashing.out
export ARTIFACTSWAP_GRADLE_MAX_MEMORY=17408

# 1. Hash all files to determine which modules have changed
echo "===== Hashing Files ====="
tools/artifactswap/artifactswap hashing \
  --logging debug \
  --hashing-output-file "${ARTIFACTSWAP_HASHING_FILE}" \
  --gradle-max-memory ${ARTIFACTSWAP_GRADLE_MAX_MEMORY} \
  --gradle-jvm-args "-XX:MaxMetaspaceSize=1g" \
  --log-gradle

# 2. Find the publish tasks that need to run
echo "===== Finding Tasks to Publish ====="
tools/artifactswap/artifactswap task-finder \
  --logging debug \
  --gradle-max-memory ${ARTIFACTSWAP_GRADLE_MAX_MEMORY} \
  --gradle-args "-Partifactswap.publishingEnabled=true -Partifactswap.artifactVersionFile=${ARTIFACTSWAP_HASHING_FILE}" \
  --gradle-jvm-args "-XX:MaxMetaspaceSize=1g" \
  --task-list-output-directory "taskOutputs" \
  --task publishToArtifactSwapRepository \
  --log-gradle \
  --output-mode "SINGLE_TASK_LIST"

# 3. Check which artifacts already exist in Artifactory
echo "===== Checking Artifacts ====="
tools/artifactswap/artifactswap artifact-checker \
  --logging debug \
  --hash-file "${ARTIFACTSWAP_HASHING_FILE}" \
  --input-file "taskOutputs/task-output.out" \
  --output-file "taskOutputs/task-output-filtered.out"

# 4. Publish only the artifacts that don't already exist
echo "===== Publishing Artifacts ====="
tools/artifactswap/artifactswap task-runner \
  --logging debug \
  --gradle-max-memory ${ARTIFACTSWAP_GRADLE_MAX_MEMORY} \
  --gradle-args "-Partifactswap.publishingEnabled=true -Partifactswap.artifactVersionFile=${ARTIFACTSWAP_HASHING_FILE}" \
  --gradle-jvm-args "-XX:MaxMetaspaceSize=1g" \
  --task-list-file "taskOutputs/task-output-filtered.out" \
  --log-gradle
```

### Step 2: Publishing the BOM
After artifacts are published, publish the BOM to Artifactory and update the BOM tracking branch:

```bash
#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset

export ARTIFACTSWAP_TOOL_OPTS="-Xmx3072M"
# This file should be consumed as an artifact from the previous pipeline stage
export ARTIFACTSWAP_HASHING_FILE=artifactswapHashes/artifactswapHashing.out
export BOM_BRANCH=artifact-swap-green-main  # Your BOM branch name from gradle.properties

echo "===== Publishing BOM ====="
tools/artifactswap/artifactswap bom-publisher \
  --logging debug \
  --bom-version "${GIT_COMMIT}" \
  --hash-file-location "${ARTIFACTSWAP_HASHING_FILE}"

echo "===== Updating BOM Branch ====="
# Update the BOM tracking branch to point to the current commit
# This allows developers to find the most recent BOM for their branch
git fetch origin "${BOM_BRANCH}" || true
git branch -f "${BOM_BRANCH}" "${GIT_COMMIT}"
git push origin "${BOM_BRANCH}"
```

> [!TIP]
> Configure your CI pipeline to only run the BOM publishing job after all your other check tasks have completed successfully. This will prevent compilation issues from "poisoning" the IDE artifacts.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and contribution guidelines.

## License
See [LICENSE](LICENSE) for license information.

[shrinking-elephants]: https://engineering.block.xyz/blog/shrinking-elephants
[airbnb-droidcon-talk]: https://www.droidcon.com/2023/10/06/supercharging-ide-and-sync-performance/
[spotlight]: https://github.com/joshfriend/spotlight
[spotlight-ide-plugin]: https://plugins.jetbrains.com/plugin/27451-spotlight
