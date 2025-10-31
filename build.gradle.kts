plugins {
  id("com.android.lint") apply false
  id("com.vanniktech.maven.publish") apply false
  id("com.gradleup.shadow") apply false
}

dependencyAnalysis {
  reporting {
    printBuildHealth(true)
  }
  issues {
    all {
      onAny {
        severity("fail")
      }
    }
  }
}
