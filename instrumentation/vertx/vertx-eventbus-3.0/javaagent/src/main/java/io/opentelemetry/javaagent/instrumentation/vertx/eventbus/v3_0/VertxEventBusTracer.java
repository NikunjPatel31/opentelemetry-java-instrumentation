/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.vertx.core.eventbus.Message;

/** Tracer for Vert.x EventBus operations. */
public class VertxEventBusTracer {

  private static final Tracer TRACER =
      GlobalOpenTelemetry.getTracer("io.opentelemetry.vertx-eventbus-3.0");

  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");
  private static final AttributeKey<String> MESSAGING_DESTINATION =
      AttributeKey.stringKey("messaging.destination");
  private static final AttributeKey<String> MESSAGING_DESTINATION_KIND =
      AttributeKey.stringKey("messaging.destination_kind");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<Boolean> MESSAGING_VERTX_EVENTBUS =
      AttributeKey.booleanKey("messaging.vertx.eventbus");
  private static final AttributeKey<String> MESSAGING_MESSAGE_PAYLOAD_SIZE =
      AttributeKey.stringKey("messaging.message.payload.size");

  private VertxEventBusTracer() {}

  /** Start a span for sending a message. */
  public static Context startSend(String address, Object message, String operation) {
    SpanBuilder spanBuilder =
        TRACER
            .spanBuilder("vertx.eventbus." + operation)
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MESSAGING_SYSTEM, "vertx-eventbus")
            .setAttribute(MESSAGING_DESTINATION, address)
            .setAttribute(MESSAGING_DESTINATION_KIND, "topic")
            .setAttribute(MESSAGING_OPERATION, operation)
            .setAttribute(MESSAGING_VERTX_EVENTBUS, true);

    if (message != null) {
      spanBuilder.setAttribute(
          MESSAGING_MESSAGE_PAYLOAD_SIZE, String.valueOf(message.toString().length()));
    }

    Span span = spanBuilder.startSpan();
    return Context.current().with(span);
  }

  /** Start a span for receiving a message. */
  public static Context startReceive(Message<?> message) {
    String address = message.address();

    SpanBuilder spanBuilder =
        TRACER
            .spanBuilder("vertx.eventbus.receive")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MESSAGING_SYSTEM, "vertx-eventbus")
            .setAttribute(MESSAGING_DESTINATION, address)
            .setAttribute(MESSAGING_DESTINATION_KIND, "topic")
            .setAttribute(MESSAGING_OPERATION, "receive")
            .setAttribute(MESSAGING_VERTX_EVENTBUS, true);

    Span span = spanBuilder.startSpan();
    return Context.current().with(span);
  }

  /** Start a span for handling a message. */
  public static Context startHandle(Message<?> message) {
    String address = message.address();

    SpanBuilder spanBuilder =
        TRACER
            .spanBuilder("vertx.eventbus.handle")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MESSAGING_SYSTEM, "vertx-eventbus")
            .setAttribute(MESSAGING_DESTINATION, address)
            .setAttribute(MESSAGING_DESTINATION_KIND, "topic")
            .setAttribute(MESSAGING_OPERATION, "process")
            .setAttribute(MESSAGING_VERTX_EVENTBUS, true);

    Span span = spanBuilder.startSpan();
    return Context.current().with(span);
  }

  /** End a span. */
  public static void end(Context context, Throwable throwable) {
    Span span = Span.fromContext(context);
    if (throwable != null) {
      span.recordException(throwable);
      span.setStatus(StatusCode.ERROR);
    }
    span.end();
  }
}
