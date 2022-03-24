// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package org.apache.spark.ml

object LanguageType extends Enumeration {

  type LanguageType = Value
  val Python = Value("Python")
  val R = Value("R")

  def getExtension(language: LanguageType): String = {
    language match {
      case LanguageType.Python => ".py"
      case LanguageType.R => ".R"
      case _ => throw new MatchError(s"$language is not a recognized language")
    }
  }
}