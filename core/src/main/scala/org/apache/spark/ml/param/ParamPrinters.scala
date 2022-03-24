// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package org.apache.spark.ml.param

import spray.json._

import java.lang.{StringBuilder => JStringBuilder}
import org.apache.spark.ml.LanguageType.LanguageType


trait PythonPrinter extends CompactPrinter {
  override protected def printLeaf(x: JsValue, sb: JStringBuilder): Unit = {
    x match {
      case JsNull      => sb.append("None")
      case JsTrue      => sb.append("True")
      case JsFalse     => sb.append("False")
      case JsNumber(x) => sb.append(x)
      case JsString(x) => printString(x, sb)
      case _           => throw new IllegalStateException
    }
  }
}

object PythonPrinter extends PythonPrinter

trait RPrinter extends CompactPrinter {
  override protected def printArray(elements: Seq[JsValue], sb: JStringBuilder): Unit = {
    sb.append("c(")
    printSeq(elements, sb.append(','))(print(_, sb))
    sb.append(")")
  }

  override protected def printLeaf(x: JsValue, sb: JStringBuilder): Unit = {
    x match {
      case JsNull      => sb.append("NULL")
      case JsTrue      => sb.append("TRUE")
      case JsFalse     => sb.append("FALSE")
      case JsNumber(x) => sb.append(x)
      case JsString(x) => printString(x, sb)
      case _           => throw new IllegalStateException
    }
  }
}

object RPrinter extends RPrinter
