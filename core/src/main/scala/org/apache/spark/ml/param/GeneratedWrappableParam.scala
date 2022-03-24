// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package org.apache.spark.ml.param

import org.apache.spark.ml.LanguageType
import spray.json._

import java.lang.{StringBuilder => JStringBuilder}
import org.apache.spark.ml.LanguageType.LanguageType
//import org.apache.spark.ml.param.{PythonPrinter, RPrinter}

object GeneratedWrappableParam {

  def defaultRender[T](value: T, language: LanguageType, jsonFunc: T => String): String = {
    val parsed = jsonFunc(value).parseJson
    language match {
      case LanguageType.Python => PythonPrinter(parsed)
      case LanguageType.R => RPrinter(parsed)
      case _ => throw new MatchError(s"$language is not a recognized language")
    }
  }

  // TODO: we cannot include a language argument in an implicit parameter list. Figure out an alternative
  def defaultRender[T](value: T)(implicit dataFormat: JsonFormat[T]): String = {
    defaultRender(value, LanguageType.Python, { v: T => v.toJson.compactPrint })
  }

  def defaultRender[T](value: T, language: LanguageType, param: Param[T]): String = {
    defaultRender(value, language, { v: T => param.jsonEncode(v) })
  }

}

trait GeneratedWrappableParam[T] extends Param[T] {

  val name: String

  type InnerType = T

  def generatedValue(v: T): String

  def generatedName(v: T): String = {
    name
  }

  def constructorLine(v: T): String = {
    s"""${generatedName(v)}=${generatedValue(v)}"""
  }

  def setterLine(v: T): String = {
    s"""set${generatedName(v).capitalize}(${generatedValue(v)})"""
  }

}

trait ExternalGeneratedWrappableParam[T] extends GeneratedWrappableParam[T] {

  def loadLine(modelNum: Int, language: LanguageType): String

}
