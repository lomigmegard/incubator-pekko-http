/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.server.directives

import java.util.concurrent.{ CompletionException, CompletionStage }

import org.apache.pekko
import pekko.dispatch.ExecutionContexts
import pekko.http.javadsl.marshalling.Marshaller

import scala.annotation.varargs
import pekko.http.impl.model.JavaUri
import pekko.http.javadsl.model.{
  HttpHeader,
  HttpRequest,
  HttpResponse,
  RequestEntity,
  ResponseEntity,
  StatusCode,
  Uri
}
import pekko.http.javadsl.server.{ Rejection, Route, RoutingJavaMapping }
import pekko.http.scaladsl
import pekko.http.scaladsl.marshalling.Marshaller._
import pekko.http.scaladsl.marshalling.ToResponseMarshallable
import pekko.http.scaladsl.model.StatusCodes.Redirection
import pekko.http.javadsl.server.RoutingJavaMapping._
import pekko.http.scaladsl.server.RouteResult
import pekko.http.scaladsl.server.directives.{ RouteDirectives => D }
import pekko.http.scaladsl.util.FastFuture
import pekko.http.scaladsl.util.FastFuture._
import scala.concurrent.ExecutionContext

abstract class RouteDirectives extends RespondWithDirectives {
  import RoutingJavaMapping.Implicits._

  // Don't try this at home – we only use it here for the java -> scala conversions
  private implicit val conversionExecutionContext: ExecutionContext = ExecutionContexts.sameThreadExecutionContext

  /**
   * Java-specific call added so you can chain together multiple alternate routes using comma,
   * rather than having to explicitly call route1.orElse(route2).orElse(route3).
   * @deprecated Use the `RouteDirectives.concat` method instead.
   */
  @Deprecated
  @deprecated("Use the RouteDirectives.concat method instead.", "Akka HTTP 10.1.6")
  @CorrespondsTo("concat")
  @varargs def route(alternatives: Route*): Route = RouteAdapter {
    import pekko.http.scaladsl.server.Directives._

    if (alternatives.isEmpty)
      throw new IllegalArgumentException("Chaining empty list of routes is illegal.")

    alternatives.map(_.delegate).reduce(_ ~ _)
  }

  /**
   * Used to chain multiple alternate routes using comma,
   * rather than having to explicitly call route1.orElse(route2).orElse(route3).
   */
  @varargs def concat(first: Route, alternatives: Route*): Route = RouteAdapter {
    import pekko.http.scaladsl.server.Directives._

    (first +: alternatives).map(_.delegate).reduce(_ ~ _)
  }

  /**
   * Rejects the request with the given rejections, or with an empty set of rejections if no rejections are given.
   */
  @varargs def reject(rejection: Rejection, rejections: Rejection*): Route = RouteAdapter {
    D.reject((rejection +: rejections).map(_.asScala): _*)
  }

  /**
   * Rejects the request with an empty rejection (usually used for "no directive matched").
   */
  def reject(): Route = RouteAdapter {
    D.reject()
  }

  /**
   * Completes the request with redirection response of the given type to the given URI.
   *
   * @param redirectionType A status code from StatusCodes, which must be a redirection type.
   */
  def redirect(uri: Uri, redirectionType: StatusCode): Route = RouteAdapter {
    redirectionType match {
      case r: Redirection => D.redirect(uri.asInstanceOf[JavaUri].uri, r)
      case _              => throw new IllegalArgumentException("Not a valid redirection status code: " + redirectionType)
    }
  }

  /**
   * Bubbles the given error up the response chain, where it is dealt with by the closest `handleExceptions`
   * directive and its ExceptionHandler.
   */
  def failWith(error: Throwable): Route = RouteAdapter(D.failWith(error))

  /**
   * Completes the request using an HTTP 200 OK status code and the given body as UTF-8 entity.
   */
  def complete(body: String): Route = RouteAdapter(
    D.complete(body))

  /**
   * Completes the request using the given http response.
   */
  def complete(response: HttpResponse): Route = RouteAdapter(
    D.complete(response.asScala))

  /**
   * Completes the request using the given status code.
   */
  def complete(status: StatusCode): Route = RouteAdapter(
    D.complete(status.asScala))

  /**
   * Completes the request by marshalling the given value into an http response.
   */
  def complete[T](value: T, marshaller: Marshaller[T, HttpResponse]) = RouteAdapter {
    D.complete(ToResponseMarshallable(value)(marshaller))
  }

  /**
   * Completes the request using the given status code and headers, marshalling the given value as response entity.
   */
  def complete[T](status: StatusCode, headers: java.lang.Iterable[HttpHeader], value: T,
      marshaller: Marshaller[T, RequestEntity]) = RouteAdapter {
    D.complete(ToResponseMarshallable(value)(fromToEntityMarshaller(status.asScala, headers.asScala)(marshaller)))
  }

  /**
   * Completes the request using the given status code, headers, and response entity.
   */
  def complete(status: StatusCode, headers: java.lang.Iterable[HttpHeader], entity: ResponseEntity) = RouteAdapter {
    D.complete(scaladsl.model.HttpResponse(status = status.asScala, entity = entity.asScala, headers = headers.asScala))
  }

  /**
   * Completes the request using the given status code, headers, and response entity.
   */
  def complete(status: StatusCode, headers: java.lang.Iterable[HttpHeader], entity: RequestEntity): RouteAdapter = {
    complete(status, headers, entity: ResponseEntity)
  }

  /**
   * Completes the request using the given status code, marshalling the given value as response entity.
   */
  def complete[T](status: StatusCode, value: T, marshaller: Marshaller[T, RequestEntity]) = RouteAdapter {
    D.complete(ToResponseMarshallable(value)(fromToEntityMarshaller(status.asScala)(marshaller)))
  }

  /**
   * Completes the request using the given status code and response entity.
   */
  def complete(status: StatusCode, entity: ResponseEntity) = RouteAdapter {
    D.complete(scaladsl.model.HttpResponse(status = status.asScala, entity = entity.asScala))
  }

  /**
   * Completes the request using the given status code and response entity.
   */
  def complete(status: StatusCode, entity: RequestEntity): RouteAdapter = complete(status, entity: ResponseEntity)

  /**
   * Completes the request using the given status code and the given body as UTF-8.
   */
  def complete(status: StatusCode, entity: String) = RouteAdapter {
    D.complete(scaladsl.model.HttpResponse(status = status.asScala, entity = entity))
  }

  /**
   * Completes the request as HTTP 200 OK, adding the given headers, and marshalling the given value as response entity.
   */
  def complete[T](headers: java.lang.Iterable[HttpHeader], value: T, marshaller: Marshaller[T, RequestEntity]) =
    RouteAdapter {
      D.complete(ToResponseMarshallable(value)(fromToEntityMarshaller(headers = headers.asScala)(marshaller)))
    }

  /**
   * Completes the request as HTTP 200 OK, adding the given headers and response entity.
   */
  def complete(headers: java.lang.Iterable[HttpHeader], entity: ResponseEntity) = RouteAdapter {
    D.complete(scaladsl.model.HttpResponse(headers = headers.asScala, entity = entity.asScala))
  }

  /**
   * Completes the request as HTTP 200 OK, adding the given headers and response entity.
   */
  def complete(headers: java.lang.Iterable[HttpHeader], entity: RequestEntity): RouteAdapter =
    complete(headers, entity: ResponseEntity)

  /**
   * Completes the request as HTTP 200 OK, marshalling the given value as response entity.
   */
  @CorrespondsTo("complete")
  def completeOK[T](value: T, marshaller: Marshaller[T, RequestEntity]) = RouteAdapter {
    D.complete(ToResponseMarshallable(value)(fromToEntityMarshaller()(marshaller)))
  }

  /**
   * Completes the request as HTTP 200 OK with the given value as response entity.
   */
  def complete(entity: ResponseEntity) = RouteAdapter {
    D.complete(scaladsl.model.HttpResponse(entity = entity.asScala))
  }

  /**
   * Completes the request as HTTP 200 OK with the given value as response entity.
   */
  def complete(entity: RequestEntity): RouteAdapter = complete(entity: ResponseEntity)

  // --- manual "magnet" for Scala Future ---

  /**
   * Completes the request by marshalling the given future value into an http response.
   */
  @CorrespondsTo("complete")
  def completeWithFutureResponse(value: scala.concurrent.Future[HttpResponse]) = RouteAdapter {
    D.complete(value.fast.map(_.asScala))
  }

  /**
   * Completes the request by marshalling the given future value into an http response.
   */
  @CorrespondsTo("complete")
  def completeOKWithFutureString(value: scala.concurrent.Future[String]) = RouteAdapter {
    D.complete(value)
  }

  /**
   * Completes the request using the given future status code.
   */
  @CorrespondsTo("complete")
  def completeWithFutureStatus(status: scala.concurrent.Future[StatusCode]): Route = RouteAdapter {
    D.complete(status.fast.map(_.asScala))
  }

  /**
   * Completes the request by marshalling the given value into an http response.
   */
  @CorrespondsTo("complete")
  def completeOKWithFuture[T](value: scala.concurrent.Future[T], marshaller: Marshaller[T, RequestEntity]) =
    RouteAdapter {
      D.complete(value.fast.map(v => ToResponseMarshallable(v)(fromToEntityMarshaller()(marshaller))))
    }

  /**
   * Completes the request by marshalling the given value into an http response.
   */
  @CorrespondsTo("complete")
  def completeWithFuture[T](value: scala.concurrent.Future[T], marshaller: Marshaller[T, HttpResponse]) = RouteAdapter {
    D.complete(value.fast.map(v => ToResponseMarshallable(v)(marshaller)))
  }

  // --- manual "magnet" for CompletionStage ---

  /**
   * Completes the request by marshalling the given future value into an http response.
   */
  @CorrespondsTo("complete")
  def completeWithFuture(value: CompletionStage[HttpResponse]) = RouteAdapter {
    D.complete(value.asScala.fast.map((h: HttpResponse) => h.asScala).recover(unwrapCompletionException))
  }

  /**
   * Completes the request by marshalling the given future value into an http response.
   */
  @CorrespondsTo("complete")
  def completeOKWithFuture(value: CompletionStage[RequestEntity]) = RouteAdapter {
    D.complete(value.asScala.fast.map((r: RequestEntity) => r.asScala).recover(unwrapCompletionException))
  }

  /**
   * Completes the request by marshalling the given future value into an http response.
   */
  @CorrespondsTo("complete")
  def completeOKWithFutureString(value: CompletionStage[String]) = RouteAdapter {
    D.complete(value.asScala.recover(unwrapCompletionException))
  }

  /**
   * Completes the request using the given future status code.
   */
  @CorrespondsTo("complete")
  def completeWithFutureStatus(status: CompletionStage[StatusCode]): Route = RouteAdapter {
    D.complete(status.asScala.fast.map((s: StatusCode) => s.asScala).recover(unwrapCompletionException))
  }

  /**
   * Completes the request with an `OK` status code by marshalling the given value into an http response.
   */
  @CorrespondsTo("complete")
  def completeOKWithFuture[T](value: CompletionStage[T], marshaller: Marshaller[T, RequestEntity]) = RouteAdapter {
    D.complete(value.asScala.fast.map(v => ToResponseMarshallable(v)(fromToEntityMarshaller()(marshaller))).recover(
      unwrapCompletionException))
  }

  /**
   * Completes the request by marshalling the given value into an http response.
   */
  @CorrespondsTo("complete")
  def completeWithFuture[T](value: CompletionStage[T], marshaller: Marshaller[T, HttpResponse]) = RouteAdapter {
    D.complete(value.asScala.fast.map(v => ToResponseMarshallable(v)(marshaller)).recover(unwrapCompletionException))
  }

  /**
   * Handle the request using a function.
   */
  def handle(handler: pekko.japi.function.Function[HttpRequest, CompletionStage[HttpResponse]]): Route = {
    import pekko.http.impl.util.JavaMapping._
    RouteAdapter { ctx =>
      handler(ctx.request).asScala.fast.map((response: HttpResponse) => RouteResult.Complete(response.asScala))
    }
  }

  /**
   * Handle the request using a function.
   */
  def handleSync(handler: pekko.japi.function.Function[HttpRequest, HttpResponse]): Route = {
    import pekko.http.impl.util.JavaMapping._
    RouteAdapter { ctx => FastFuture.successful(RouteResult.Complete(handler(ctx.request).asScala)) }
  }

  // TODO: This might need to be raised as an issue to scala-java8-compat instead.
  // Right now, having this in Java:
  //     CompletableFuture.supplyAsync(() -> { throw new IllegalArgumentException("always failing"); })
  // will in fact fail the future with CompletionException.
  private def unwrapCompletionException[T]: PartialFunction[Throwable, T] = {
    case x: CompletionException if x.getCause ne null =>
      throw x.getCause
  }

}
