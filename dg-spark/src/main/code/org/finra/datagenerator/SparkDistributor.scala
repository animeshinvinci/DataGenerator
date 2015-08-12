/*
 * Copyright 2014 DataGenerator Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finra.datagenerator

import java._
import java.io.{ObjectOutputStream, OutputStream}

import org.finra.datagenerator.consumer.{DataPipe, DataConsumer}
import org.finra.datagenerator.writer.{DefaultWriter, DataWriter}

import scala.collection.JavaConverters._

import org.apache.spark.{SparkConf, SparkContext}
import org.finra.datagenerator.distributor.SearchDistributor
import org.finra.datagenerator.engine.Frontier

/**
 * Take Frontiers produced by an Engine and process the Frontiers in parallel,
 * giving the results to a DataConsumer for post-processing.
 *
 * Set masterURL to SparkConf using Parametrized Constructor
 *
 * Created by Brijesh on 6/2/2015.
 */
class SparkDistributor(masterURL: String, scalaDataConsumer: ScalaDataConsumer) extends
  SearchDistributor with java.io.Serializable {

  val flag: Boolean = true

  val randomNumberQueue = new util.LinkedList[util.Map[String, String]]()

  /**
   * Set Data Consumer to consume output
   *
   * @param dataConsumer data consumer
   * @return SearchDistributor
   */
  def setDataConsumer(dataConsumer: DataConsumer): SearchDistributor = {
    this
  }

  /**
   * Distribute the random number generated by Frontier from Frontier List
   * Process data parallel using Spark - Map, Reduce method
   *
   * Define SparkConfiguration and SparkContext,
   * Add dependency jar
   *
   * @param frontierList list of Frontiers
   */
  def distribute(frontierList: util.List[Frontier]): Unit = {

    println("Frontier list size = " + (frontierList.size() - 1))  // scalastyle:ignore

    val conf: SparkConf = new SparkConf().setMaster(masterURL).setAppName("dg-spark-example")

    val sparkContext: SparkContext = new SparkContext(conf)

    /**
     * Add jar file to resolve dependencies
     * NOTE: if you change something compile maven file from command line using
     * "mvn clean package"
     */
    sparkContext.addJar("./dg-spark/target/dg-spark-2.2-SNAPSHOT.jar")

      for (frontier <- frontierList.asScala) {

        sparkContext.parallelize(1 to RandomNumberEngine.numberInEachFrontier).foreach(_ => {
          
            //Generate Random Number Parallel
            searchWorker(frontier, randomNumberQueue, flag)

            //Call Consume method for parallel processing
            produceOutput()

        })
      }
  }

  /**
   * While Queue is not empty
   * Pop the elements from the Queue and give it to consume method
   */
  def produceOutput(): Unit = {

    var lines: Int = 0

    val os: OutputStream = System.out
    val objectOS = new ObjectOutputStream(os)

    while(!randomNumberQueue.isEmpty) {

      this.synchronized {

        val maps = randomNumberQueue.remove()

        lines += scalaDataConsumer.consume(maps)

        println()  // scalastyle:ignore
      }
    }
    println()  // scalastyle:ignore
  }

  /**
   * Call the searchForScenario Method which generates random number
   *
   * @param frontier frontier object
   * @param javaQueue Queue
   * @param flag boolean
   */
  def searchWorker(frontier: Frontier, javaQueue: util.Queue[util.Map[String, String]], flag: Boolean): Unit = {

    frontier.searchForScenarios(javaQueue, null)   // scalastyle:ignore

  }
}