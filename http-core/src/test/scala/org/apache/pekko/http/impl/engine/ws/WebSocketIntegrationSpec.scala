/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.ws

import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration.DurationInt
import org.apache.pekko
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.Uri.apply
import pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import pekko.http.scaladsl.model.ws._
import pekko.stream._
import pekko.stream.scaladsl._
import pekko.stream.testkit._
import pekko.stream.scaladsl.GraphDSL.Implicits._
import org.scalatest.concurrent.Eventually

import java.net.InetSocketAddress
import pekko.Done
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler }
import pekko.util.ByteString
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit._

import scala.util.{ Failure, Success }

class WebSocketIntegrationSpec extends PekkoSpecWithMaterializer(
      """
     pekko {
       stream.materializer.debug.fuzzing-mode=off
       http.server.websocket.log-frames = on
       http.client.websocket.log-frames = on
     }
  """) with Eventually {

  "A WebSocket server" must {

    "not reset the connection when no data are flowing" in Utils.assertAllStagesStopped {
      val source = TestPublisher.probe[Message]()
      val bindingFuture = Http().newServerAt("localhost", 0).bindSync {
        _.attribute(webSocketUpgrade).get.handleMessages(Flow.fromSinkAndSource(Sink.ignore,
          Source.fromPublisher(source)), None)
      }
      val binding = Await.result(bindingFuture, 3.seconds.dilated)
      val myPort = binding.localAddress.getPort

      val (response, sink) = Http().singleWebSocketRequest(
        WebSocketRequest("ws://127.0.0.1:" + myPort),
        Flow.fromSinkAndSourceMat(TestSink.probe[Message], Source.empty)(Keep.left))

      response.futureValue.response.status.isSuccess should ===(true)
      sink
        .request(10)
        .expectNoMessage(500.millis)

      source
        .sendNext(TextMessage("hello"))
        .sendComplete()
      sink
        .expectNext(TextMessage("hello"))
        .expectComplete()

      binding.unbind()
    }

    "not reset the connection when no data are flowing and the connection is closed from the client" in Utils.assertAllStagesStopped {
      val source = TestPublisher.probe[Message]()
      val bindingFuture = Http().newServerAt("localhost", 0).bindSync {
        _.attribute(webSocketUpgrade).get.handleMessages(Flow.fromSinkAndSource(Sink.ignore,
          Source.fromPublisher(source)), None)
      }
      val binding = Await.result(bindingFuture, 3.seconds.dilated)
      val myPort = binding.localAddress.getPort

      val completeOnlySwitch: Flow[ByteString, ByteString, Promise[Done]] = Flow.fromGraph(
        new GraphStageWithMaterializedValue[FlowShape[ByteString, ByteString], Promise[Done]] {
          override val shape: FlowShape[ByteString, ByteString] =
            FlowShape(Inlet("completeOnlySwitch.in"), Outlet("completeOnlySwitch.out"))

          override def createLogicAndMaterializedValue(
              inheritedAttributes: Attributes): (GraphStageLogic, Promise[Done]) = {
            val promise = Promise[Done]()

            val logic = new GraphStageLogic(shape) with InHandler with OutHandler {
              override def onPush(): Unit = push(shape.out, grab(shape.in))
              override def onPull(): Unit = pull(shape.in)

              override def preStart(): Unit = {
                promise.future.foreach(_ => getAsyncCallback[Done](_ => complete(shape.out)).invoke(Done))(
                  pekko.dispatch.ExecutionContexts.sameThreadExecutionContext)
              }

              setHandlers(shape.in, shape.out, this)
            }

            (logic, promise)
          }
        })

      val ((response, breaker), sink) =
        Source.empty
          .viaMat {
            Http().webSocketClientLayer(WebSocketRequest("ws://localhost:" + myPort))
              .atop(TLSPlacebo())
              .joinMat(completeOnlySwitch.via(
                Tcp(system).outgoingConnection(new InetSocketAddress("localhost", myPort), halfClose = true)))(
                Keep.both)
          }(Keep.right)
          .toMat(TestSink.probe[Message])(Keep.both)
          .run()

      response.futureValue.response.status.isSuccess should ===(true)
      sink
        .request(10)
        .expectNoMessage(1500.millis)

      breaker.trySuccess(Done)

      source
        .sendNext(TextMessage("hello"))
        .sendComplete()
      sink
        .expectNext(TextMessage("hello"))
        .expectComplete()

      binding.unbind()
    }

    "echo 100 elements and then shut down without error" in Utils.assertAllStagesStopped {

      val bindingFuture = Http().newServerAt("localhost", 0).bindSync {
        _.attribute(webSocketUpgrade).get.handleMessages(Flow.apply, None)
      }
      val binding = Await.result(bindingFuture, 3.seconds.dilated)
      val myPort = binding.localAddress.getPort

      val N = 100

      EventFilter.warning(pattern = "HTTP header .* is not allowed in responses", occurrences = 0).intercept {
        val (response, count) = Http().singleWebSocketRequest(
          WebSocketRequest("ws://127.0.0.1:" + myPort),
          Flow.fromSinkAndSourceMat(
            Sink.fold(0)((n, _: Message) => n + 1),
            Source.repeat(TextMessage("hello")).take(N))(Keep.left))
        count.futureValue should ===(N)
      }

      binding.unbind()
    }

    "send back 100 elements and then terminate without error even when not ordinarily closed" in Utils.assertAllStagesStopped {
      val N = 100

      val handler = Flow.fromGraph(GraphDSL.create() { implicit b =>
        val merge = b.add(Merge[Int](2))

        // convert to int so we can connect to merge
        val mapMsgToInt = b.add(Flow[Message].map(_ => -1))
        val mapIntToMsg = b.add(Flow[Int].map(x => TextMessage.Strict(s"Sending: $x")))

        // source we want to use to send message to the connected websocket sink
        val rangeSource = b.add(Source(1 to N))

        mapMsgToInt ~> merge // this part of the merge will never provide msgs
        rangeSource ~> merge ~> mapIntToMsg

        FlowShape(mapMsgToInt.in, mapIntToMsg.out)
      })

      val bindingFuture = Http().newServerAt("localhost", 0).bindSync {
        _.attribute(webSocketUpgrade).get.handleMessages(handler, None)
      }
      val binding = Await.result(bindingFuture, 3.seconds.dilated)
      val myPort = binding.localAddress.getPort

      @volatile var messages = 0
      val (switch, completion) =
        Source.maybe
          .viaMat {
            Http().webSocketClientLayer(WebSocketRequest("ws://localhost:" + myPort))
              .atop(TLSPlacebo())
              // the resource leak of #19398 existed only for severed websocket connections
              .atopMat(KillSwitches.singleBidi[ByteString, ByteString])(Keep.right)
              .join(Tcp(system).outgoingConnection(new InetSocketAddress("localhost", myPort), halfClose = true))
          }(Keep.right)
          .toMat(Sink.foreach(_ => messages += 1))(Keep.both)
          .run()
      eventually(messages should ===(N))
      // breaker should have been fulfilled long ago
      switch.shutdown()
      completion.futureValue

      binding.unbind()
    }

    "terminate the handler flow with an error when the connection is aborted" in Utils.assertAllStagesStopped {
      val handlerTermination = Promise[Done]()

      val handler = Flow[Message]
        .watchTermination()(Keep.right)
        .mapMaterializedValue(handlerTermination.completeWith(_))
        .map(m => TextMessage.Strict(s"Echo [${m.asTextMessage.getStrictText}]"))

      val bindingFuture =
        Http().newServerAt("localhost", 0).bindSync(_.attribute(webSocketUpgrade).get.handleMessages(handler, None))
      val binding = Await.result(bindingFuture, 3.seconds.dilated)
      val myPort = binding.localAddress.getPort

      val clientMessageOut = TestPublisher.probe[Message]()
      val clientMessageIn = TestSubscriber.probe[Message]()

      val switch =
        Source.fromPublisher(clientMessageOut)
          .viaMat {
            Http().webSocketClientLayer(WebSocketRequest("ws://localhost:" + myPort))
              .atop(TLSPlacebo())
              .atopMat(KillSwitches.singleBidi[ByteString, ByteString])(Keep.right)
              .join(Tcp(system).outgoingConnection(new InetSocketAddress("localhost", myPort), halfClose = true))
          }(Keep.right)
          .toMat(Sink.fromSubscriber(clientMessageIn))(Keep.left)
          .run()

      // simulate message exchange to make sure handler has been installed
      clientMessageOut.sendNext(TextMessage("Test"))
      clientMessageIn.requestNext(TextMessage("Echo [Test]"))

      switch.abort(new IllegalStateException("Connection aborted"))

      // Should fail, not complete:
      handlerTermination.future.failed.futureValue

      binding.unbind()
    }

  }

  "A websocket client" should {
    "fail the materialized future if the request fails" in {
      val flow = Http().webSocketClientFlow(
        WebSocketRequest("ws://127.0.0.1:65535/no/server/here"),
        settings = ClientConnectionSettings(system).withConnectingTimeout(250.millis.dilated))

      val future = Source.maybe[Message].viaMat(flow)(Keep.right).to(Sink.ignore).run()
      import system.dispatcher
      whenReady(future.map(r => Success(r)).recover { case ex => Failure(ex) }) { resTry =>
        resTry.isFailure should ===(true)
        resTry.failed.get.getMessage should ===("Connection failed.")
      }
    }
  }

}
