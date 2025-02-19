/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine

import java.util.concurrent.CountDownLatch

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.dispatch.ExecutionContexts
import pekko.http.CommonBenchmark
import pekko.http.scaladsl.model.{ ContentTypes, HttpEntity }
import pekko.stream.scaladsl.Source
import pekko.stream.{ ActorMaterializer, Materializer }
import pekko.util.ByteString
import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations.{ Benchmark, Param, Setup, TearDown }

class HttpEntityBenchmark extends CommonBenchmark {
  @Param(Array("strict", "default"))
  var entityType: String = _

  implicit var system: ActorSystem = _
  implicit var mat: Materializer = _

  var entity: HttpEntity = _

  @Benchmark
  def discardBytes(): Unit = {
    val latch = new CountDownLatch(1)
    entity.discardBytes(mat)
      .future
      .onComplete(_ => latch.countDown())(ExecutionContexts.parasitic)
    latch.await()
  }

  private val chunk = ByteString(new Array[Byte](10000))
  @Setup
  def setup(): Unit = {
    val config =
      ConfigFactory.parseString(
        """
           pekko.actor.default-dispatcher.fork-join-executor.parallelism-max = 1
        """)
        .withFallback(ConfigFactory.load())
    system = ActorSystem("AkkaHttpBenchmarkSystem", config)
    mat = ActorMaterializer()

    entity = entityType match {
      case "strict" =>
        HttpEntity.Strict(ContentTypes.`application/octet-stream`, chunk)
      case "default" =>
        HttpEntity.Default(
          ContentTypes.`application/octet-stream`,
          10 * chunk.size,
          Source.repeat(chunk).take(10))
    }
  }

  @TearDown
  def tearDown(): Unit = system.terminate()
}
