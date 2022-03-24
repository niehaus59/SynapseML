// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package org.apache.spark.ml.param

import com.microsoft.azure.synapse.ml.core.serialize.ComplexParam
import com.microsoft.azure.synapse.ml.core.utils.{ModelEquality, ParamEquality}
import org.apache.spark.ml.{Estimator, Model, PipelineStage, LanguageType}
import org.apache.spark.ml.LanguageType.LanguageType

trait PipelineStageWrappable[T <: PipelineStage] extends ExternalGeneratedWrappableParam[T] with ParamEquality[T] {

  override def generatedValue(v: T): String = {
    s"""${name}Model"""
  }

  override def loadLine(modelNum: Int, language: LanguageType): String = {
    language match {
      case LanguageType.Python =>
        s"""
        |from pyspark.ml import Pipeline
        |${name}Model = Pipeline.load(join(test_data_dir, "model-$modelNum.model", "complexParams", "$name"))
        |${name}Model = ${name}Model.getStages()[0]
        |""".stripMargin
      case LanguageType.R =>
        s"""
        |${name}Model <- ml_load(sc, paste(test_data_dir, "model-$modelNum.model", "complexParams", "$name", sep = "/"))
        |${name}Model <- head(ml_stages(${name}Model))
        |""".stripMargin
      case _ =>
        throw new MatchError(s"$language is not a recognized language")
    }
  }

  override def assertEquality(v1: Any, v2: Any): Unit = {
    (v1, v2) match {
      case (e1: PipelineStage, e2: PipelineStage) =>
        ModelEquality.assertEqual(e1,e2)
      case _ =>
        throw new AssertionError("Values do not extend from PipelineStage type")
    }
  }

}

/** Param for Estimator.  Needed as spark has explicit params for many different
  * types but not Estimator.
  */
class EstimatorParam(parent: Params, name: String, doc: String, isValid: Estimator[_ <: Model[_]] => Boolean)
  extends ComplexParam[Estimator[_ <: Model[_]]](parent, name, doc, isValid)
    with PipelineStageWrappable[Estimator[_ <: Model[_]]] {

    def this(parent: Params, name: String, doc: String) =
      this(parent, name, doc, ParamValidators.alwaysTrue)

}
