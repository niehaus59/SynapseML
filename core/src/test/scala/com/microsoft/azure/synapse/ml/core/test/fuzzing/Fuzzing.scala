// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package com.microsoft.azure.synapse.ml.core.test.fuzzing


import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.apache.commons.io.FileUtils
import org.apache.spark.ml._
import org.apache.spark.ml.param.{DataFrameEquality, ExternalGeneratedWrappableParam, ParamPair}
import org.apache.spark.ml.util.{MLReadable, MLWritable}
import org.apache.spark.sql.DataFrame
import org.apache.spark.ml.LanguageType._
import com.microsoft.azure.synapse.ml.codegen.CodegenConfig
import com.microsoft.azure.synapse.ml.codegen.GenerationUtils._
import com.microsoft.azure.synapse.ml.core.env.FileUtilities
import com.microsoft.azure.synapse.ml.core.test.base.TestBase

/**
  * Class for holding test information, call by name to avoid unnecessary computations in test generations
  *
  * @param stage         Pipeline stage for testing
  * @param fitDFArg      Dataframe to fit
  * @param transDFArg    Dataframe to transform
  * @param validateDFArg Optional dataframe to validate against
  * @tparam S The type of the stage
  */
class TestObject[S <: PipelineStage](val stage: S,
                                     fitDFArg: => DataFrame,
                                     transDFArg: => DataFrame,
                                     validateDFArg: => Option[DataFrame]) {
  lazy val fitDF: DataFrame = fitDFArg
  lazy val transDF: DataFrame = transDFArg
  lazy val validateDF: Option[DataFrame] = validateDFArg

  def this(stage: S, df: => DataFrame) = {
    this(stage, df, df, None)
  }

  def this(stage: S, fitDF: => DataFrame, transDF: => DataFrame) = {
    this(stage, fitDF, transDF, None)
  }

}

/*object LanguageType extends Enumeration {

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
}*/

trait GeneratedTestFuzzing[S <: PipelineStage] extends TestBase with DataFrameEquality {

  def testObjects(): Seq[TestObject[S]]

  val testClassName: String = this.getClass.getName.split(".".toCharArray).last

  def testDataDir(conf: CodegenConfig): File = FileUtilities.join(
    conf.testDataDir, this.getClass.getName.split(".".toCharArray).last)

  def saveDataset(conf: CodegenConfig, df: DataFrame, name: String): Unit = {
    df.write.mode("overwrite").parquet(new File(testDataDir(conf), s"$name.parquet").toString)
  }

  def saveModel(conf: CodegenConfig, model: S, name: String): Unit = {
    model match {
      case writable: MLWritable =>
        writable.write.overwrite().save(new File(testDataDir(conf), s"$name.model").toString)
      case _ =>
        throw new IllegalArgumentException(s"${model.getClass.getName} is not writable")
    }

  }

  val testFitting = false

  def saveTestData(conf: CodegenConfig): Unit = {
    testDataDir(conf).mkdirs()
    testObjects().zipWithIndex.foreach { case (to, i) =>
      saveModel(conf, to.stage, s"model-$i")
      if (testFitting) {
        saveDataset(conf, to.fitDF, s"fit-$i")
        saveDataset(conf, to.transDF, s"trans-$i")
        to.validateDF.foreach(saveDataset(conf, _, s"val-$i"))
      }
    }
  }

  def testInstantiateModel(stage: S, num: Int, language: LanguageType): String = {
    val fullParamMap = stage.extractParamMap().toSeq
    val partialParamMap = stage.extractParamMap().toSeq.filter(pp => stage.get(pp.param).isDefined)
    val stageName = stage.getClass.getName.split(".".toCharArray).last

    println(s"stage: $stageName, language: $language")
    try {
      instantiateModel(fullParamMap, stageName, num, language)
    } catch {
      case _: NotImplementedError =>
        println(s"could not generate full $language test for $stageName, resorting to partial test")
        instantiateModel(partialParamMap, stageName, num, language)
    }
  }

  def makeTestFile(conf: CodegenConfig, language: LanguageType, saveData: Boolean): Unit = {
    spark
    if (saveData) saveTestData(conf)
    val stage = testObjects().head.stage
    val stageName = stage.getClass.getName.split(".".toCharArray).last
    val importPath = stage.getClass.getName.split(".".toCharArray).dropRight(1)
    val importPathString = importPath.mkString(".").replaceAllLiterally("com.microsoft.azure.synapse.ml", "synapse.ml")
    val generatedTests = testObjects().zipWithIndex.map { case (to, i) => makeTests(to, i, language) }
    val testClass = getTestClass(conf, generatedTests, stage, stageName, importPathString, language)
    val testFolders = importPath.mkString(".")
      .replaceAllLiterally("com.microsoft.azure.synapse.ml", "synapsemltest").split(".".toCharArray)
    val baseTestDir = getTestDir(conf, language)
    val fileExtension = getFileExtension(language)
    val testDir = FileUtilities.join((Seq(baseTestDir.toString) ++ testFolders.toSeq): _*)
    testDir.mkdirs()
    Files.write(
      FileUtilities.join(testDir, "test_" + camelToSnake(testClassName) + fileExtension).toPath,
      testClass.getBytes(StandardCharsets.UTF_8))
  }

  // TODO: figure out how to use ordinary class hierarchy to delegate
  // while still extending the Fuzzing trait with both Python and R subclasses.
  private def getTestClass(conf: CodegenConfig,
                           generatedTests: Seq[String],
                           stage: S,
                           stageName: String,
                           importPathString: String,
                           language: LanguageType): String = {
    language match {
      case LanguageType.Python => getPyTestClass(conf, generatedTests, stage, stageName, importPathString)
      case LanguageType.R => getRTestClass(conf, generatedTests, stage, stageName, importPathString)
      case _ => throw new MatchError(s"$language is not a recognized language")
    }
  }

  private def instantiateModel(paramMap: Seq[ParamPair[_]], stageName: String, num: Int, language: LanguageType): String = {
    language match {
      case LanguageType.Python => instantiatePyModel(paramMap, stageName, num)
      case LanguageType.R => instantiateRModel(paramMap, stageName, num)
      case _ => throw new MatchError(s"$language is not a recognized language")
    }
  }

  private def makeTests(testObject: TestObject[S], num: Int, language: LanguageType): String = {
    language match {
      case LanguageType.Python => makePyTests(testObject, num)
      case LanguageType.R => makeRTests(testObject, num)
      case _ => throw new MatchError(s"$language is not a recognized language")
    }
  }

  private def getPyTestClass(conf: CodegenConfig,
                             generatedTests: Seq[String],
                             stage: S,
                             stageName: String,
                             importPathString: String): String = {
    s"""import unittest
       |from synapsemltest.spark import *
       |from $importPathString import $stageName
       |from os.path import join
       |import json
       |import mlflow
       |from pyspark.ml import PipelineModel
       |
       |test_data_dir = "${testDataDir(conf).toString.replaceAllLiterally("\\", "\\\\")}"
       |
       |
       |class $testClassName(unittest.TestCase):
       |    def assert_correspondence(self, model, name, num):
       |        model.write().overwrite().save(join(test_data_dir, name))
       |        sc._jvm.com.microsoft.azure.synapse.ml.core.utils.ModelEquality.assertEqual(
       |            "${stage.getClass.getName}",
       |            str(join(test_data_dir, name)),
       |            str(join(test_data_dir, "model-{}.model".format(num)))
       |        )
       |
       |${indent(generatedTests.mkString("\n\n"), 1)}
       |
       |if __name__ == "__main__":
       |    result = unittest.main()
       |
       |""".stripMargin
  }

  // TODO: update from py to R
  private def getRTestClass(conf: CodegenConfig,
                            generatedTests: Seq[String],
                            stage: S,
                            stageName: String,
                            importPathString: String): String = {
    s"""import unittest
       |from synapsemltest.spark import *
       |from $importPathString import $stageName
       |from os.path import join
       |import json
       |import mlflow
       |from pyspark.ml import PipelineModel
       |
       |test_data_dir = "${testDataDir(conf).toString.replaceAllLiterally("\\", "\\\\")}"
       |
       |
       |class $testClassName(unittest.TestCase):
       |    def assert_correspondence(self, model, name, num):
       |        model.write().overwrite().save(join(test_data_dir, name))
       |        sc._jvm.com.microsoft.azure.synapse.ml.core.utils.ModelEquality.assertEqual(
       |            "${stage.getClass.getName}",
       |            str(join(test_data_dir, name)),
       |            str(join(test_data_dir, "model-{}.model".format(num)))
       |        )
       |
       |${indent(generatedTests.mkString("\n\n"), 1)}
       |
       |if __name__ == "__main__":
       |    result = unittest.main()
       |
       |""".stripMargin
  }

  private def instantiatePyModel(paramMap: Seq[ParamPair[_]], stageName: String, num: Int): String = {
    val externalLoadLines = getLoadLine(paramMap, num)
    s"""
       |$externalLoadLines
       |
       |model = $stageName(
       |${indent(paramMap.map(pyRenderParam(_)).mkString(",\n"), 1)}
       |)
       |
       |""".stripMargin
  }

  def instantiateRModel(paramMap: Seq[ParamPair[_]], stageName: String, num: Int): String = {
    val externalLoadLines = getLoadLine(paramMap, num)
    s"""
       |$externalLoadLines
       |
       |model = $stageName(
       |${indent(paramMap.map(rRenderParam(_)).mkString(",\n"), 1)}
       |)
       |
       |""".stripMargin
  }

  private def getLoadLine(paramMap: Seq[ParamPair[_]], num: Int): String = {
    paramMap.flatMap { pp =>
      pp.param match {
        case ep: ExternalGeneratedWrappableParam[_] =>
          Some(ep.loadLine(num, LanguageType.Python))
        case _ => None
      }
    }.mkString("\n")
  }

  def makePyTests(testObject: TestObject[S], num: Int): String = {
    val stage = testObject.stage
    val stageName = stage.getClass.getName.split(".".toCharArray).last
    val fittingTest = stage match {
      case _: Estimator[_] if testFitting =>
        s"""
           |fdf = spark.read.parquet(join(test_data_dir, "fit-$num.parquet"))
           |tdf = spark.read.parquet(join(test_data_dir, "trans-$num.parquet"))
           |model.fit(fdf).transform(tdf).show()
           |""".stripMargin
      case _: Transformer if testFitting =>
        s"""
           |tdf = spark.read.parquet(join(test_data_dir, "trans-$num.parquet"))
           |model.transform(tdf).show()
           |""".stripMargin
      case _ => ""
    }
    val mlflowTest = stage match {
      case _: Model[_] =>
        s"""
           |mlflow.spark.save_model(model, "mlflow-save-model-$num")
           |mlflow.spark.log_model(model, "mlflow-log-model-$num")
           |mlflow_model = mlflow.pyfunc.load_model("mlflow-save-model-$num")
           |""".stripMargin
      case _: Transformer => stage.getClass.getName.split(".".toCharArray).dropRight(1).last match {
        case "cognitive" =>
          s"""
             |pipeline_model = PipelineModel(stages=[model])
             |mlflow.spark.save_model(pipeline_model, "mlflow-save-model-$num")
             |mlflow.spark.log_model(pipeline_model, "mlflow-log-model-$num")
             |mlflow_model = mlflow.pyfunc.load_model("mlflow-save-model-$num")
             |""".stripMargin
        case _ => ""
      }
      case _ => ""
    }

    s"""
       |def test_${stageName}_constructor_$num(self):
       |${indent(testInstantiateModel(stage, num, LanguageType.Python), 1)}
       |
       |    self.assert_correspondence(model, "py-constructor-model-$num.model", $num)
       |
       |${indent(fittingTest, 1)}
       |
       |${indent(mlflowTest, 1)}
       |
       |""".stripMargin
  }

  // TODO: update from py to R
  //noinspection ScalaStyle
  def makeRTests(testObject: TestObject[S], num: Int): String = {
    val stage = testObject.stage
    val stageName = stage.getClass.getName.split(".".toCharArray).last
    val fittingTest = stage match {
      case _: Estimator[_] if testFitting =>
        s"""
           |fdf <- read.parquet(paste(test_data_dir, "fit-$num.parquet", sep = "/"))
           |tdf <- read.parquet(paste(test_data_dir, "trans-$num.parquet, sep = "/"))
           |
           |showDF(?)
           |model.fit(fdf).transform(tdf).show()
           |""".stripMargin
      case _: Transformer if testFitting =>
        s"""
           |tdf <- read.parquet(paste(test_data_dir, "trans-$num.parquet", sep = "/"))
           |model.transform(tdf).show()
           |""".stripMargin
      case _ => ""
    }
    val mlflowTest = stage match {
      case _: Model[_] =>
        s"""
           |mlflow.spark.save_model(model, "mlflow-save-model-$num")
           |mlflow.spark.log_model(model, "mlflow-log-model-$num")
           |mlflow_model <- mlflow.pyfunc.load_model("mlflow-save-model-$num")
           |""".stripMargin
      case _: Transformer => stage.getClass.getName.split(".".toCharArray).dropRight(1).last match {
        case "cognitive" =>
          s"""
             |pipeline_model <- PipelineModel(stages=[model])
             |mlflow.spark.save_model(pipeline_model, "mlflow-save-model-$num")
             |mlflow.spark.log_model(pipeline_model, "mlflow-log-model-$num")
             |mlflow_model = mlflow.pyfunc.load_model("mlflow-save-model-$num")
             |""".stripMargin
        case _ => ""
      }
      case _ => ""
    }

    s"""
       |def test_${stageName}_constructor_$num(self):
       |${indent(testInstantiateModel(stage, num, LanguageType.R), 1)}
       |
       |    self.assert_correspondence(model, "py-constructor-model-$num.model", $num)
       |
       |${indent(fittingTest, 1)}
       |
       |${indent(mlflowTest, 1)}
       |
       |""".stripMargin
  }

  private def getTestDir(conf: CodegenConfig, language: LanguageType): File = {
    language match {
      case LanguageType.Python => conf.pyTestDir
      case LanguageType.R => conf.rTestDir
      case _ => throw new MatchError(s"$language is not a recognized language")
    }
  }

  private def getFileExtension(language: LanguageType) : String = {
    language match {
      case LanguageType.Python => ".py"
      case LanguageType.R => ".R"
      case _ => throw new MatchError(s"$language is not a recognized language")
    }
  }
}

/*
trait PyTestFuzzing[S <: PipelineStage] extends LanguageTestFuzzing[S] {
  override def testLanguage: String = "Python"
  override def fileExtension: String = "py"
  override def testBaseDir(conf: CodegenConfig): File = conf.pyTestDir

  //def pyTestObjects(): Seq[TestObject[S]] = super.testObjects()

  override def instantiateModel(paramMap: Seq[ParamPair[_]], stageName: String, num: Int): String = {
    val externalLoadLines = paramMap.flatMap { pp =>
      pp.param match {
        case ep: ExternalPythonWrappableParam[_] =>
          Some(ep.pyLoadLine(num))
        case _ => None
      }
    }.mkString("\n")
    s"""
       |$externalLoadLines
       |
       |model = $stageName(
       |${indent(paramMap.map(pyRenderParam(_)).mkString(",\n"), 1)}
       |)
       |
       |""".stripMargin
  }

  //noinspection ScalaStyle
  override def makeTests(testObject: TestObject[S], num: Int): String = {
    val stage = testObject.stage
    val stageName = stage.getClass.getName.split(".".toCharArray).last
    val fittingTest = stage match {
      case _: Estimator[_] if testFitting =>
        s"""
           |fdf = spark.read.parquet(join(test_data_dir, "fit-$num.parquet"))
           |tdf = spark.read.parquet(join(test_data_dir, "trans-$num.parquet"))
           |model.fit(fdf).transform(tdf).show()
           |""".stripMargin
      case _: Transformer if testFitting =>
        s"""
           |tdf = spark.read.parquet(join(test_data_dir, "trans-$num.parquet"))
           |model.transform(tdf).show()
           |""".stripMargin
      case _ => ""
    }
    val mlflowTest = stage match {
      case _: Model[_] =>
        s"""
           |mlflow.spark.save_model(model, "mlflow-save-model-$num")
           |mlflow.spark.log_model(model, "mlflow-log-model-$num")
           |mlflow_model = mlflow.pyfunc.load_model("mlflow-save-model-$num")
           |""".stripMargin
      case _: Transformer => stage.getClass.getName.split(".".toCharArray).dropRight(1).last match {
        case "cognitive" =>
          s"""
             |pipeline_model = PipelineModel(stages=[model])
             |mlflow.spark.save_model(pipeline_model, "mlflow-save-model-$num")
             |mlflow.spark.log_model(pipeline_model, "mlflow-log-model-$num")
             |mlflow_model = mlflow.pyfunc.load_model("mlflow-save-model-$num")
             |""".stripMargin
        case _ => ""
      }
      case _ => ""
    }

    s"""
       |def test_${stageName}_constructor_$num(self):
       |${indent(testInstantiateModel(stage, num), 1)}
       |
       |    self.assert_correspondence(model, "py-constructor-model-$num.model", $num)
       |
       |${indent(fittingTest, 1)}
       |
       |${indent(mlflowTest, 1)}
       |
       |""".stripMargin
  }

  def getTestClass(conf: CodegenConfig,
                   generatedTests: Seq[String],
                   stage: S,
                   stageName: String,
                   importPathString: String): String = {
     s"""import unittest
       |from synapsemltest.spark import *
       |from $importPathString import $stageName
       |from os.path import join
       |import json
       |import mlflow
       |from pyspark.ml import PipelineModel
       |
       |test_data_dir = "${testDataDir(conf).toString.replaceAllLiterally("\\", "\\\\")}"
       |
       |
       |class $testClassName(unittest.TestCase):
       |    def assert_correspondence(self, model, name, num):
       |        model.write().overwrite().save(join(test_data_dir, name))
       |        sc._jvm.com.microsoft.azure.synapse.ml.core.utils.ModelEquality.assertEqual(
       |            "${stage.getClass.getName}",
       |            str(join(test_data_dir, name)),
       |            str(join(test_data_dir, "model-{}.model".format(num)))
       |        )
       |
       |${indent(generatedTests.mkString("\n\n"), 1)}
       |
       |if __name__ == "__main__":
       |    result = unittest.main()
       |
       |""".stripMargin
  }
}
*/


/*
trait RTestFuzzing[S <: PipelineStage] extends LanguageTestFuzzing[S] {
override def testLanguage: String = "R"
override def fileExtension: String = "R"
override def testBaseDir(conf: CodegenConfig): File = conf.rTestDir

override def instantiateModel(paramMap: Seq[ParamPair[_]], stageName: String, num: Int): String = {
  val externalLoadLines = paramMap.flatMap { pp =>
    pp.param match {
      case rp: ExternalRWrappableParam[_] =>
        Some(rp.rLoadLine(num))
      case _ => None
    }
  }.mkString("\n")
  s"""
     |$externalLoadLines
     |
     |model = $stageName(
     |${indent(paramMap.map(rRenderParam(_)).mkString(",\n"), 1)}
     |)
     |
     |""".stripMargin
}

// TODO: update from py to R
//noinspection ScalaStyle
override def makeTests(testObject: TestObject[S], num: Int): String = {
  val stage = testObject.stage
  val stageName = stage.getClass.getName.split(".".toCharArray).last
  val fittingTest = stage match {
    case _: Estimator[_] if testFitting =>
      s"""
         |fdf <- read.parquet(join(test_data_dir, "fit-$num.parquet"))
         |tdf <- read.parquet(join(test_data_dir, "trans-$num.parquet))
         |
         |showDF(?)
         |model.fit(fdf).transform(tdf).show()
         |""".stripMargin
    case _: Transformer if testFitting =>
      s"""
         |tdf = spark.read.parquet(join(test_data_dir, "trans-$num.parquet"))
         |model.transform(tdf).show()
         |""".stripMargin
    case _ => ""
  }
  val mlflowTest = stage match {
    case _: Model[_] =>
      s"""
         |mlflow.spark.save_model(model, "mlflow-save-model-$num")
         |mlflow.spark.log_model(model, "mlflow-log-model-$num")
         |mlflow_model = mlflow.pyfunc.load_model("mlflow-save-model-$num")
         |""".stripMargin
    case _: Transformer => stage.getClass.getName.split(".".toCharArray).dropRight(1).last match {
      case "cognitive" =>
        s"""
           |pipeline_model = PipelineModel(stages=[model])
           |mlflow.spark.save_model(pipeline_model, "mlflow-save-model-$num")
           |mlflow.spark.log_model(pipeline_model, "mlflow-log-model-$num")
           |mlflow_model = mlflow.pyfunc.load_model("mlflow-save-model-$num")
           |""".stripMargin
      case _ => ""
    }
    case _ => ""
  }

  s"""
     |def test_${stageName}_constructor_$num(self):
     |${indent(testInstantiateModel(stage, num), 1)}
     |
     |    self.assert_correspondence(model, "py-constructor-model-$num.model", $num)
     |
     |${indent(fittingTest, 1)}
     |
     |${indent(mlflowTest, 1)}
     |
     |""".stripMargin
}

// TODO: update from py to R
def getTestClass(conf: CodegenConfig,
                 generatedTests: Seq[String],
                 stage: S,
                 stageName: String,
                 importPathString: String): String = {
   s"""import unittest
     |from synapsemltest.spark import *
     |from $importPathString import $stageName
     |from os.path import join
     |import json
     |import mlflow
     |from pyspark.ml import PipelineModel
     |
     |test_data_dir = "${testDataDir(conf).toString.replaceAllLiterally("\\", "\\\\")}"
     |
     |
     |class $testClassName(unittest.TestCase):
     |    def assert_correspondence(self, model, name, num):
     |        model.write().overwrite().save(join(test_data_dir, name))
     |        sc._jvm.com.microsoft.azure.synapse.ml.core.utils.ModelEquality.assertEqual(
     |            "${stage.getClass.getName}",
     |            str(join(test_data_dir, name)),
     |            str(join(test_data_dir, "model-{}.model".format(num)))
     |        )
     |
     |${indent(generatedTests.mkString("\n\n"), 1)}
     |
     |if __name__ == "__main__":
     |    result = unittest.main()
     |
     |""".stripMargin
  }
}
*/

trait ExperimentFuzzing[S <: PipelineStage] extends TestBase with DataFrameEquality {

  def experimentTestObjects(): Seq[TestObject[S]]

  def runExperiment(s: S, fittingDF: DataFrame, transformingDF: DataFrame): DataFrame = {
    s match {
      case t: Transformer =>
        t.transform(transformingDF)
      case e: Estimator[_] =>
        e.fit(fittingDF).transform(transformingDF)
      case _ => throw new MatchError(s"$s is not a Transformer or Estimator")
    }
  }

  def testExperiments(): Unit = {
    experimentTestObjects().foreach { req =>
      val res = runExperiment(req.stage, req.fitDF, req.transDF)
      req.validateDF match {
        case Some(vdf) => assertDFEq(res, vdf)
        case None => ()
      }
    }
  }

  test("Experiment Fuzzing") {
    testExperiments()
  }

}

trait SerializationFuzzing[S <: PipelineStage with MLWritable] extends TestBase with DataFrameEquality {
  def serializationTestObjects(): Seq[TestObject[S]]

  def reader: MLReadable[_]

  def modelReader: MLReadable[_]

  val useShm: Boolean = sys.env.getOrElse("MMLSPARK_TEST_SHM", "false").toBoolean

  lazy val savePath: String = {
    if (useShm) {
      val f = new File(s"/dev/shm/SavedModels-${System.currentTimeMillis()}")
      f.mkdir()
      f.toString
    } else {
      Files.createTempDirectory("SavedModels-").toString
    }
  }

  val ignoreEstimators: Boolean = false

  private def testSerializationHelper(path: String,
                                      stage: PipelineStage with MLWritable,
                                      reader: MLReadable[_],
                                      fitDF: DataFrame, transDF: DataFrame): Unit = {
    try {
      stage.write.overwrite().save(path)
      assert(new File(path).exists())
      val loadedStage = reader.load(path)
      (stage, loadedStage) match {
        case (e1: Estimator[_], e2: Estimator[_]) =>
          val df1 = e1.fit(fitDF).transform(transDF)
          val df2 = e2.fit(fitDF).transform(transDF)
          assertDFEq(df1, df2)
        case (t1: Transformer, t2: Transformer) =>
          val df1 = t1.transform(transDF)
          val df2 = t2.transform(transDF)
          assertDFEq(df1, df2)
        case _ => throw new IllegalArgumentException(s"$stage and $loadedStage do not have proper types")
      }
      ()
    } finally {
      if (new File(path).exists()) FileUtils.forceDelete(new File(path))
    }
  }

  def testSerialization(): Unit = {
    try {
      serializationTestObjects().foreach { req =>
        val fitStage = req.stage match {
          case stage: Estimator[_] =>
            if (!ignoreEstimators) {
              testSerializationHelper(savePath + "/stage", stage, reader, req.fitDF, req.transDF)
            }
            stage.fit(req.fitDF).asInstanceOf[PipelineStage with MLWritable]
          case stage: Transformer => stage
          case s => throw new IllegalArgumentException(s"$s does not have correct type")
        }
        testSerializationHelper(savePath + "/fitStage", fitStage, modelReader, req.transDF, req.transDF)

        val pipe = new Pipeline().setStages(Array(req.stage.asInstanceOf[PipelineStage]))
        if (!ignoreEstimators) {
          testSerializationHelper(savePath + "/pipe", pipe, Pipeline, req.fitDF, req.transDF)
        }
        val fitPipe = pipe.fit(req.fitDF)
        testSerializationHelper(savePath + "/fitPipe", fitPipe, PipelineModel, req.transDF, req.transDF)
      }
    } finally {
      if (new File(savePath).exists) FileUtils.forceDelete(new File(savePath))
    }
  }

  test("Serialization Fuzzing") {
    testSerialization()
  }

}

trait Fuzzing[S <: PipelineStage with MLWritable] extends SerializationFuzzing[S]
  with ExperimentFuzzing[S] with GeneratedTestFuzzing[S] {

  def testObjects(): Seq[TestObject[S]]

  def pyTestObjects(): Seq[TestObject[S]] = testObjects()

  def rTestObjects(): Seq[TestObject[S]] = testObjects()

  def serializationTestObjects(): Seq[TestObject[S]] = testObjects()

  def experimentTestObjects(): Seq[TestObject[S]] = testObjects()

}

trait TransformerFuzzing[S <: Transformer with MLWritable] extends Fuzzing[S] {

  override val ignoreEstimators: Boolean = true

  override def modelReader: MLReadable[_] = reader

}

trait EstimatorFuzzing[S <: Estimator[_] with MLWritable] extends Fuzzing[S]
