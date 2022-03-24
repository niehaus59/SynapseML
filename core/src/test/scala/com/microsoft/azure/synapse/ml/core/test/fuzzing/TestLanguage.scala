package com.microsoft.azure.synapse.ml.core.test.fuzzing

object TestLanguage extends Enumeration {
  type Language = Value

  val Python, R = Value
  val Extension = Map(Python -> "py", R -> "R")
}