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

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.Done;
import org.apache.pekko.http.impl.util.JavaAccessors;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;

import java.util.concurrent.CompletionStage;

/** Represents an Http request. */
public abstract class HttpRequest
    implements HttpMessage, HttpMessage.MessageTransformations<HttpRequest> {
  /** Returns the Http method of this request. */
  public abstract HttpMethod method();

  /** Returns the Uri of this request. */
  public abstract Uri getUri();

  /** Returns the entity of this request. */
  public abstract RequestEntity entity();

  /** Returns a copy of this instance with a new method. */
  public abstract HttpRequest withMethod(HttpMethod method);

  /** Returns a copy of this instance with a new Uri. */
  public abstract HttpRequest withUri(Uri relativeUri);

  /** Returns a copy of this instance with a new Uri. */
  public abstract HttpRequest withUri(String path);

  /** Returns a copy of this instance with a new entity. */
  public abstract HttpRequest withEntity(RequestEntity entity);

  /** Returns a default request to be modified using the `withX` methods. */
  public static HttpRequest create() {
    return JavaAccessors.HttpRequest();
  }

  /** Returns a default request to the specified URI to be modified using the `withX` methods. */
  public static HttpRequest create(String uri) {
    return JavaAccessors.HttpRequest(uri);
  }

  /** A default GET request to be modified using the `withX` methods. */
  public static HttpRequest GET(String uri) {
    return create(uri);
  }

  /** A default POST request to be modified using the `withX` methods. */
  public static HttpRequest POST(String uri) {
    return create(uri).withMethod(HttpMethods.POST);
  }

  /** A default PUT request to be modified using the `withX` methods. */
  public static HttpRequest PUT(String uri) {
    return create(uri).withMethod(HttpMethods.PUT);
  }

  /** A default DELETE request to be modified using the `withX` methods. */
  public static HttpRequest DELETE(String uri) {
    return create(uri).withMethod(HttpMethods.DELETE);
  }

  /** A default HEAD request to be modified using the `withX` methods. */
  public static HttpRequest HEAD(String uri) {
    return create(uri).withMethod(HttpMethods.HEAD);
  }

  /** A default PATCH request to be modified using the `withX` methods. */
  public static HttpRequest PATCH(String uri) {
    return create(uri).withMethod(HttpMethods.PATCH);
  }

  /** A default OPTIONS request to be modified using the `withX` methods. */
  public static HttpRequest OPTIONS(String uri) {
    return create(uri).withMethod(HttpMethods.OPTIONS);
  }
}
