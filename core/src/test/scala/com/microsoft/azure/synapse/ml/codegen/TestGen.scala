// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package com.microsoft.azure.synapse.ml.codegen

import java.io.File
import CodegenConfigProtocol._
import com.microsoft.azure.synapse.ml.build.BuildInfo
import com.microsoft.azure.synapse.ml.core.env.FileUtilities._
import com.microsoft.azure.synapse.ml.core.test.fuzzing.GeneratedTestFuzzing
import com.microsoft.azure.synapse.ml.core.utils.JarLoadingUtils.instantiateServices
import org.apache.commons.io.FileUtils
import org.apache.spark.ml.LanguageType
import org.apache.spark.ml.LanguageType.LanguageType
import spray.json._


object TestGen {

  import CodeGenUtils._

  def makeInitFiles(testDir: File, language: LanguageType, packageFolder: String = ""): Unit = {
    val dir = new File(new File(testDir, "synapsemltest"), packageFolder)
    val extension = LanguageType.getExtension(language)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    writeFile(new File(dir, "__init__" + extension), "")
    dir.listFiles().filter(_.isDirectory).foreach(f =>
      makeInitFiles(testDir, language, packageFolder + "/" + f.getName)
    )
  }

  def generateTests(conf: CodegenConfig, language: LanguageType, saveData: Boolean): Unit = {
    instantiateServices[GeneratedTestFuzzing[_]](conf.jarName).foreach { ltc =>
      try {
        ltc.makeTestFile(conf, language, saveData)
      } catch {
        case e: NotImplementedError =>
          println(e)
          println(s"ERROR: Could not generate $language test for ${ltc.testClassName} because of Complex Parameters")
      }
    }
  }
/*
  def generatePyTests(conf: CodegenConfig): Unit = {
    instantiateServices[PyTestFuzzing[_]](conf.jarName).foreach { ltc =>
      try {
        ltc.makeTestFile(conf, conf.pyTestDir)
      } catch {
        case _: NotImplementedError =>
          println(s"ERROR: Could not generate R test for ${ltc.testClassName} because of Complex Parameters")
      }
    }
  }

  def generateRTests(conf: CodegenConfig): Unit = {
    instantiateServices[RTestFuzzing[_]](conf.jarName).foreach { ltc =>
      try {
        ltc.makeTestFile(conf, conf.rTestDir)
      } catch {
        case _: NotImplementedError =>
          println(s"ERROR: Could not generate Python test for ${ltc.testClassName} because of Complex Parameters")
      }
    }
  }
*/

  //noinspection ScalaStyle
  def generatePackageData(conf: CodegenConfig): Unit = {
    generatePyPackageData(conf)
    generateRPackageData(conf)
  }

  def generatePyPackageData(conf: CodegenConfig): Unit = {
    if (!conf.pySrcDir.exists()) {
      conf.pySrcDir.mkdir()
    }
    val scalaVersion = BuildInfo.scalaVersion.split(".".toCharArray).dropRight(1).mkString(".")
    val testDir = join(conf.pyTestDir, "synapsemltest")
    createDir(testDir)
    writeFile(join(testDir, "spark.py"),
      s"""
         |# Copyright (C) Microsoft Corporation. All rights reserved.
         |# Licensed under the MIT License. See LICENSE in project root for information.
         |
         |from pyspark.sql import SparkSession, SQLContext
         |import os
         |import synapse.ml
         |from synapse.ml.core import __spark_package_version__
         |
         |spark = (SparkSession.builder
         |    .master("local[*]")
         |    .appName("PysparkTests")
         |    .config("spark.jars.packages", "com.microsoft.azure:synapseml_$scalaVersion:" + __spark_package_version__)
         |    .config("spark.jars.repositories", "https://mmlspark.azureedge.net/maven")
         |    .config("spark.executor.heartbeatInterval", "60s")
         |    .config("spark.sql.shuffle.partitions", 10)
         |    .config("spark.sql.crossJoin.enabled", "true")
         |    .getOrCreate())
         |
         |sc = SQLContext(spark.sparkContext)
         |
         |""".stripMargin)
  }

  def generateRPackageData(conf: CodegenConfig): Unit = {
    if (!conf.rTestDir.exists()) {
      conf.rTestDir.mkdir()
    }
    val scalaVersion = BuildInfo.scalaVersion.split(".".toCharArray).dropRight(1).mkString(".")
    val testDir = join(conf.rTestDir, "synapsemltest")
    createDir(testDir)
    writeFile(join(testDir, "spark.R"),
      s"""
         |# Copyright (C) Microsoft Corporation. All rights reserved.
         |# Licensed under the MIT License. See LICENSE in project root for information.
         |
         |library(sparklyr)
         |library(dplyr)
         |library(ggplot2)
         |
         |coreVersion = packageVersion('synapse.ml.core)
         |
         |config <- spark_config()
         |config$$spark.jars.repositories 'https://mmlspark.azureedge.net/maven'
         |config$$spark.executor.heartbeatInterval <- '60s'
         |config$$spark.sql.shuffle.partitions = 10
         |config$$spark.sql.crossJoin.enabled = 'true'
         |
         |sc <- spark_connect(
         |  master = "local[*]",
         |  app_name = "RsparkTests"
         |  config = config,
         |  packages = cat('com.microsoft.azure:synapseml_', $scalaVersion, ':', coreVersion, sep = '')
         |  scala_version = $scalaVersion
         |
         |""".stripMargin)
  }

  def createPyTests(conf: CodegenConfig): Unit = {
    cleanDir(conf.pyTestDir)
    generateTests(conf, LanguageType.Python, saveData = true)
    //TestBase.stopSparkSession()
    generatePyPackageData(conf)
    copyOverride(conf.pyTestOverrideDir, conf.pyTestDir)
    makeInitFiles(conf.pyTestDir, LanguageType.Python, packageFolder = "py")
  }

  def createRTests(conf: CodegenConfig): Unit = {
    cleanDir(conf.rTestDir)
    generateTests(conf, LanguageType.R, saveData = false)
    //TestBase.stopSparkSession()
    generateRPackageData(conf)
    copyOverride(conf.rTestOverrideDir, conf.rTestDir)
    // TODO: check if R needs init files
  }

  private def cleanDir(testDir: File): Unit = {
    createDir(testDir)
    clean(testDir)
  }

  private def copyOverride(overrideDir: File, testDir: File): Unit = {
    if (toDir(overrideDir).exists()) {
      FileUtils.copyDirectoryToDirectory(toDir(overrideDir), toDir(testDir))
    }
  }
/*
object RTestGen extends BaseTestGen {

  import CodeGenUtils._

  def generateTests(conf: CodegenConfig): Unit = {
    instantiateServices[RTestFuzzing[_]](conf.jarName).foreach { ltc =>
      println(ltc.testClassName)
    }
    instantiateServices[RTestFuzzing[_]](conf.jarName).foreach { ltc =>
      try {
        ltc.makeTestFile(conf, conf.rTestDir.toString, "R")
      } catch {
        case _: NotImplementedError =>
          println(s"ERROR: Could not generate test for ${ltc.testClassName} because of Complex Parameters")
      }
    }
  }

  //noinspection ScalaStyle
  def generatePackageData(conf: CodegenConfig): Unit = {
    if (!conf.rTestDir.exists()) {
      conf.rTestDir.mkdir()
    }
    val scalaVersion = BuildInfo.scalaVersion.split(".".toCharArray).dropRight(1).mkString(".")
    writeFile(join(conf.rTestDir, "spark.R"),
      s"""
         |# Copyright (C) Microsoft Corporation. All rights reserved.
         |# Licensed under the MIT License. See LICENSE in project root for information.
         |
         |library(sparklyr)
         |library(dplyr)
         |library(ggplot2)
         |
         |coreVersion = packageVersion('synapse.ml.core)
         |
         |config <- spark_config()
         |config$$spark.jars.repositories 'https://mmlspark.azureedge.net/maven'
         |config$$spark.executor.heartbeatInterval <- '60s'
         |config$$spark.sql.shuffle.partitions = 10
         |config$$spark.sql.crossJoin.enabled = 'true'
         |
         |sc <- spark_connect(
         |  master = "local[*]",
         |  app_name = "RsparkTests"
         |  config = config,
         |  packages = cat('com.microsoft.azure:synapseml_', $scalaVersion, ':', coreVersion, sep = '')
         |  scala_version = $scalaVersion
         |
         |""".stripMargin)
  }

  def createTests(conf: CodegenConfig): Unit = {
    createDir(conf.rTestDir)
    clean(conf.rTestDir)
    generateTests(conf)
    TestBase.stopSparkSession()
    generatePackageData(conf)
    if (toDir(conf.rTestOverrideDir).exists()) {
      FileUtils.copyDirectoryToDirectory(toDir(conf.rTestOverrideDir), toDir(conf.rTestDir))
    }
    makeInitFiles(conf.pyTestDir, extension = "R", packageFolder = "R")
  }
}

object TestGen {

  import CodeGenUtils._
*/
  def main(args: Array[String]): Unit = {
    val conf = args.head.parseJson.convertTo[CodegenConfig]
    clean(conf.testDataDir)
    createPyTests(conf)
    createRTests(conf)
  }
}

