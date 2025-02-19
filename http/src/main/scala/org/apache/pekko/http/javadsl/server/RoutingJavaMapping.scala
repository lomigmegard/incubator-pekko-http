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

package org.apache.pekko.http.javadsl.server

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util.JavaMapping._
import pekko.http.impl.util._
import pekko.http.javadsl.common.EntityStreamingSupport
import pekko.http.{ javadsl, scaladsl }
import pekko.http.scaladsl.server.{ directives => sdirectives }
import pekko.http.scaladsl.{ common => scommon }
import pekko.http.javadsl.server.{ directives => jdirectives }

import scala.collection.immutable

/**
 * INTERNAL API
 */
@InternalApi
private[http] object RoutingJavaMapping {

  object Implicits {
    import scala.language.implicitConversions

    implicit def convertToScala[J](j: J)(implicit mapping: J2SMapping[J]): mapping.S = mapping.toScala(j)
    implicit def convertSeqToScala[J](j: Seq[J])(implicit mapping: J2SMapping[J]): immutable.Seq[mapping.S] =
      j.map(mapping.toScala(_)).toList

    implicit def AddAsScala[J](javaObject: J)(implicit mapping: J2SMapping[J]): AsScala[mapping.S] =
      new AsScala[mapping.S] {
        def asScala = convertToScala(javaObject)
      }
    implicit def AddAsJava[S](scalaObject: S)(implicit mapping: S2JMapping[S]): AsJava[mapping.J] =
      new AsJava[mapping.J] {
        def asJava = mapping.toJava(scalaObject)
      }
  }

  implicit object Rejection extends Inherited[javadsl.server.Rejection, scaladsl.server.Rejection]

  implicit object RequestContext extends JavaMapping[javadsl.server.RequestContext, scaladsl.server.RequestContext] {
    // TODO make it inherit
    //    extends Inherited[javadsl.server.RequestContext, scaladsl.server.RequestContext]
    override def toScala(javaObject: javadsl.server.RequestContext): scaladsl.server.RequestContext =
      javaObject.delegate
    override def toJava(scalaObject: scaladsl.server.RequestContext): javadsl.server.RequestContext =
      javadsl.server.RequestContext.wrap(scalaObject)
  }
  implicit object convertRouteResult extends Inherited[javadsl.server.RouteResult, scaladsl.server.RouteResult]

  implicit object convertEntityStreamingSupport
      extends Inherited[EntityStreamingSupport, scommon.EntityStreamingSupport]

  implicit object convertDirectoryRenderer
      extends Inherited[jdirectives.DirectoryRenderer, sdirectives.FileAndResourceDirectives.DirectoryRenderer]
  implicit object convertDirectoryListing extends Inherited[jdirectives.DirectoryListing, sdirectives.DirectoryListing]

  //  implicit object javaToScalaMediaType extends Inherited[javadsl.model.MediaType, scaladsl.model.MediaType]
  //  implicit object javaToScalaContentType extends Inherited[javadsl.model.ContentType, scaladsl.model.ContentType]
  //  implicit object javaToScalaHttpMethod extends Inherited[javadsl.model.HttpMethod, scaladsl.model.HttpMethod]
  //  implicit object javaToScalaHttpRequest extends Inherited[javadsl.model.HttpRequest, scaladsl.model.HttpRequest]
  //  implicit object javaToScalaRequestEntity extends Inherited[javadsl.model.RequestEntity, scaladsl.model.RequestEntity]
  //  implicit object javaToScalaHttpResponse extends Inherited[javadsl.model.HttpResponse, scaladsl.model.HttpResponse]
  //  implicit object javaToScalaStatusCode extends Inherited[javadsl.model.StatusCode, scaladsl.model.StatusCode]
  //  implicit object javaToScalaHttpEncoding extends Inherited[javadsl.model.headers.HttpEncoding, scaladsl.model.headers.HttpEncoding]
  //  implicit object javaToScalaByteRange extends Inherited[javadsl.model.headers.ByteRange, scaladsl.model.headers.ByteRange]
  //  implicit object javaToScalaHttpChallenge extends Inherited[javadsl.model.headers.HttpChallenge, scaladsl.model.headers.HttpChallenge]
  //  implicit object javaToScalaHttpHeader extends Inherited[javadsl.model.HttpHeader, scaladsl.model.HttpHeader]
  //  implicit object javaToScalaLanguage extends Inherited[javadsl.model.headers.Language, scaladsl.model.headers.Language]
  //  implicit object javaToScalaHttpCookiePair extends Inherited[javadsl.model.headers.HttpCookiePair, scaladsl.model.headers.HttpCookiePair]
  //  implicit object javaToScalaHttpCookie extends Inherited[javadsl.model.headers.HttpCookie, scaladsl.model.headers.HttpCookie]
  //  implicit object javaToScalaHttpCredentials extends Inherited[javadsl.model.headers.HttpCredentials, scaladsl.model.headers.HttpCredentials]
  //  implicit object javaToScalaMessage extends Inherited[javadsl.model.ws.Message, scaladsl.model.ws.Message]
  //  implicit object javaToScalaEntityTag extends Inherited[javadsl.model.headers.EntityTag, scaladsl.model.headers.EntityTag]
  //  implicit object javaToScalaDateTime extends Inherited[javadsl.model.DateTime, scaladsl.model.DateTime]
  implicit object convertRouteSettings
      extends Inherited[javadsl.settings.RoutingSettings, scaladsl.settings.RoutingSettings]
  implicit object convertParserSettings
      extends Inherited[javadsl.settings.ParserSettings, scaladsl.settings.ParserSettings]
  implicit object convertLogEntry
      extends Inherited[javadsl.server.directives.LogEntry, scaladsl.server.directives.LogEntry]

  //  // not made implicit since these are subtypes of RequestEntity
  //  val javaToScalaHttpEntity extends Inherited[javadsl.model.HttpEntity, scaladsl.model.HttpEntity]
  //  val javaToScalaResponseEntity extends Inherited[javadsl.model.ResponseEntity, scaladsl.model.ResponseEntity]

  implicit final class ConvertCompletionStage[T](val stage: CompletionStage[T]) extends AnyVal {
    import pekko.util.FutureConverters
    def asScala = FutureConverters.asScala(stage)
  }
}
