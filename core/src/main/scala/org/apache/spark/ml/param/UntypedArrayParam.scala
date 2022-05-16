// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package org.apache.spark.ml.param

import org.apache.spark.annotation.DeveloperApi
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat}

import scala.collection.JavaConverters._
import spray.json._

object AnyJsonFormat extends DefaultJsonProtocol {
  implicit def anyFormat: JsonFormat[Any] =
    new JsonFormat[Any] {
      def write(any: Any): JsValue = any match {
        case v: Int => v.toJson
        case v: Double => v.toJson
        case v: String => v.toJson
        case v: Boolean => v.toJson
        case v: Integer => v.toLong.toJson
        case v: Seq[Any] => {
          val x = v.toJson(seqFormat)
          x
          /*val l = v.map(w =>
            w match {
              case w: String => JsString(w)
              case _ => w
            }).toVector
          val j = JsArray(l)
          j*/
         /* JsArray(v.map(w =>
            w match {
              case x: String => JsString(x)
              case _ => write(w)
            }).toVector)*/
          //val x = JsArray(v.map(w => write(String.format(s""""%s""""", w))).toVector)
          //x
          //JsString(String.format("""["%s"]""", v.mkString(s"""","""")))
          //JsString(String.format("[%s]", v.mkString(",")))
          //val x = write(v.map(w => write(w)))
          //x
        }
        case _ => throw new IllegalArgumentException(s"Cannot serialize ${any} of type ${any.getClass}")
      }

      def read(value: JsValue): Any = value match {
        case v: JsNumber =>
          val num = v.value
          num match {
            case _ if num.isValidInt => num.toInt
            case _ if num.isValidLong => num.toLong
            case _ if num.isExactDouble || num.isBinaryDouble || num.isDecimalDouble => num.toDouble
            case _ => num
          }
        case v: JsString => v.value
        case v: JsBoolean => v.value
        case v: JsArray => seqFormat.read(v)
        case _ => throw new IllegalArgumentException(s"Cannot deserialize ${value}")
      }
    }

  implicit def seqFormat: JsonFormat[Seq[Any]] =
    new JsonFormat[Seq[Any]] {
      def write(any: Seq[Any]): JsValue = {
        val x = JsArray(any.map(v => v.toJson).toVector)
        x
      }

      def read(value: JsValue): Seq[Any] = value match {
        case v: JsArray => v.elements.map(w => anyFormat.read(v))
        case _ => throw new IllegalArgumentException(s"${value} is not a JsArray; cannot deserialize")
      }
    }

}

/** :: DeveloperApi ::
  * Specialized generic version of `Param[Array[_]]` for Java.
  */
@DeveloperApi
class UntypedArrayParam(parent: Params, name: String, doc: String, isValid: Array[Any] => Boolean)
  extends Param[Array[Any]](parent, name, doc, isValid) {
    import AnyJsonFormat._

    def this(parent: Params, name: String, doc: String) =
      this(parent, name, doc, ParamValidators.alwaysTrue)

    def w(value: java.util.ArrayList[_]): ParamPair[Array[Any]] =
      w(value.asScala.toArray.asInstanceOf[Array[Any]])

    def w(value: java.util.List[_]): ParamPair[Array[Any]] =
      w(value.asScala.toArray.asInstanceOf[Array[Any]])

    override def jsonEncode(value: Array[Any]): String = {
      value.toJson.compactPrint
    }

    override def jsonDecode(json: String): Array[Any] = {
      json.parseJson.convertTo[Array[Any]]
    }
  }
