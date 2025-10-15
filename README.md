# artifact-swap README

## Introduction

Artifact Swap is a tool that solves the problem of "Gradle build/sync is slow" for large Gradle projects.

The core idea is to minimize the time Gradle spends in its Configuration Phase. This is achieved by taking declared gradle projects and, just before they are set to be included/configured, swapping them with a published artifact. Doing this swap in a way to respects what, specifically, the developer wants to work on can result in a large reduction in the number of full gradle projects participating in the build. And if you manage the publishing and downloading of these artifacts reasonably well, you can avoid the problem of dependency resolution causing artifact-swap-based builds to be slow themselves.

Artifact Swap exists to help teams set up this swapping system with minimal effort.

## Project Resources

| Resource                                   | Description                                                                    |
| ------------------------------------------ | ------------------------------------------------------------------------------ |
| [CODEOWNERS](./CODEOWNERS)                 | Outlines the project lead(s)                                                   |
| [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) | Expected behavior for project contributors, promoting a welcoming environment |
| [CONTRIBUTING.md](./CONTRIBUTING.md)       | Developer guide to build, test, run, access CI, chat, discuss, file issues     |
| [GOVERNANCE.md](./GOVERNANCE.md)           | Project governance                                                             |
| [LICENSE](./LICENSE)                       | Apache License, Version 2.0                                                    |
