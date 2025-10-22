plugins {
  id("com.android.lint") apply false
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
