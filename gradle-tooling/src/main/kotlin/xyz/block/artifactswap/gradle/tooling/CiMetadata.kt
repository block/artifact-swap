package xyz.block.artifactswap.gradle.tooling

/** CI metadata for analytics. */
data class CiMetadata(
  val gitBranch: String = System.getenv("GIT_BRANCH").orEmpty(),
  val gitSha: String = System.getenv("GIT_COMMIT").orEmpty(),
  val ciEnv: String = System.getenv("KOCHIKU_ENV").orEmpty(),
  val buildId: String = "",
  val buildStepId: String = "",
  val buildJobId: String = "",
  val ciType: String = "",
)
