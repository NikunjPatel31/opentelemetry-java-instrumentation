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
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Tracer for Vert.x EventBus operations.
 */
public final class VertxEventBusTracer {

  private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("io.opentelemetry.vertx-eventbus-3.0");

  private static final AttributeKey<String> MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system");
  private static final AttributeKey<String> MESSAGING_DESTINATION = AttributeKey.stringKey("messaging.destination");
  private static final AttributeKey<String> MESSAGING_DESTINATION_KIND = AttributeKey.stringKey("messaging.destination_kind");
  private static final AttributeKey<String> MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_MESSAGE_ID = AttributeKey.stringKey("messaging.message.id");
  private static final AttributeKey<String> MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES = AttributeKey.stringKey("messaging.message.payload.size");
  private static final AttributeKey<String> MESSAGING_VERTX_EVENTBUS = AttributeKey.stringKey("messaging.vertx.eventbus");

  private static final String CONTEXT_KEY = "vertx-eventbus-context";
  private static final String OPERATION_SEND = "send";
  private static final String OPERATION_PUBLISH = "publish";
  private static final String OPERATION_REQUEST = "request";
  private static final String OPERATION_RECEIVE = "receive";
  private static final String OPERATION_PROCESS = "process";

  // Getter for extracting context from Message headers
  private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<Map<String, String>>() {
    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
      return carrier.keySet();
    }

    @Nullable
    @Override
    public String get(@Nullable Map<String, String> carrier, String key) {
      return carrier == null ? null : carrier.get(key);
    }
  };

  // Setter for injecting context into Message headers
  private static final TextMapSetter<Map<String, String>> SETTER = new TextMapSetter<Map<String, String>>() {
    @Override
    public void set(@Nullable Map<String, String> carrier, String key, String value) {
      if (carrier != null) {
        carrier.put(key, value);
      }
    }
  };

  private VertxEventBusTracer() {}

  /**
   * Start a span for sending a message.
   *
   * @param address the destination address
   * @param message the message being sent
   * @param operation the operation type (send, publish, request)
   * @return the new context with the send span
   */
  public static Context startSend(String address, Object message, String operation) {
    SpanBuilder spanBuilder = TRACER.spanBuilder("vertx.eventbus." + operation)
        .setSpanKind(SpanKind.PRODUCER)
        .setAttribute(MESSAGING_SYSTEM, "vertx-eventbus")
        .setAttribute(MESSAGING_DESTINATION, address)
        .setAttribute(MESSAGING_DESTINATION_KIND, "topic")
        .setAttribute(MESSAGING_OPERATION, operation)
        .setAttribute(MESSAGING_VERTX_EVENTBUS, true);

    // Add message size if available
    if (message != null) {
      try {
        spanBuilder.setAttribute(MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, String.valueOf(message.toString().length()));
      } catch (Exception e) {
        // Ignore if we can't get the message size
      }
    }

    Span span = spanBuilder.startSpan();
    Context context = Context.current().with(span);

    // Inject the context into the message headers if possible
    try {
      injectContextIntoMessage(message, context);
    } catch (Exception e) {
      // If we can't inject the context, we'll still trace but the spans won't be connected
    }

    return context;
  }

  /**
   * Start a span for receiving a message.
   *
   * @param message the message being received
   * @return the new context with the receive span
   */
  public static Context startReceive(Object message) {
    // Extract the parent context from the message if possible
    Context parentContext = extractContextFromMessage(message);
    if (parentContext == null) {
      parentContext = Context.current();
    }

    String address = getAddressFromMessage(message);
    
    Span span = TRACER.spanBuilder("vertx.eventbus.receive")
        .setSpanKind(SpanKind.CONSUMER)
        .setAttribute(MESSAGING_SYSTEM, "vertx-eventbus")
        .setAttribute(MESSAGING_DESTINATION, address)
        .setAttribute(MESSAGING_DESTINATION_KIND, "topic")
        .setAttribute(MESSAGING_OPERATION, OPERATION_RECEIVE)
        .setAttribute(MESSAGING_VERTX_EVENTBUS, true)
        .setParent(parentContext)
        .startSpan();

    return parentContext.with(span);
  }

  /**
   * Start a span for handling a message.
   *
   * @param message the message being handled
   * @return the new context with the handle span
   */
  public static Context startHandle(Object message) {
    // Extract the parent context from the message if possible
    Context parentContext = extractContextFromMessage(message);
    if (parentContext == null) {
      parentContext = Context.current();
    }

    String address = getAddressFromMessage(message);
    
    Span span = TRACER.spanBuilder("vertx.eventbus.handle")
        .setSpanKind(SpanKind.CONSUMER)
        .setAttribute(MESSAGING_SYSTEM, "vertx-eventbus")
        .setAttribute(MESSAGING_DESTINATION, address)
        .setAttribute(MESSAGING_DESTINATION_KIND, "topic")
        .setAttribute(MESSAGING_OPERATION, OPERATION_PROCESS)
        .setAttribute(MESSAGING_VERTX_EVENTBUS, true)
        .setParent(parentContext)
        .startSpan();

    return parentContext.with(span);
  }

  /**
   * End the current span.
   *
   * @param context the context containing the span to end
   * @param throwable any exception that occurred, or null if successful
   */
  public static void end(Context context, Throwable throwable) {
    if (context == null) {
      return;
    }

    Span span = Span.fromContext(context);
    if (throwable != null) {
      span.recordException(throwable);
      span.setStatus(StatusCode.ERROR, throwable.getMessage());
    }
    span.end();
  }

  /**
   * Extract the address from a message.
   *
   * @param message the message
   * @return the address, or "unknown" if it can't be determined
   */
  private static String getAddressFromMessage(Object message) {
    if (message == null) {
      return "unknown";
    }

    try {
      Method addressMethod = message.getClass().getMethod("address");
      Object address = addressMethod.invoke(message);
      return address != null ? address.toString() : "unknown";
    } catch (Exception e) {
      return "unknown";
    }
  }

  /**
   * Extract the context from a message's headers.
   *
   * @param message the message
   * @return the extracted context, or null if it couldn't be extracted
   */
  @Nullable
  private static Context extractContextFromMessage(Object message) {
    if (message == null) {
      return null;
    }

    try {
      // Try to get headers from the message
      Method headersMethod = message.getClass().getMethod("headers");
      Object headersObj = headersMethod.invoke(message);
      
      if (headersObj != null) {
        // Convert headers to a Map<String, String>
        Map<String, String> headers = new HashMap<>();
        
        // Try to handle MultiMap style headers
        try {
          Method namesMethod = headersObj.getClass().getMethod("names");
          Object namesObj = namesMethod.invoke(headersObj);
          
          if (namesObj instanceof Iterable) {
            Method getMethod = headersObj.getClass().getMethod("get", String.class);
            
            for (Object name : (Iterable<?>) namesObj) {
              String nameStr = name.toString();
              Object value = getMethod.invoke(headersObj, nameStr);
              if (value != null) {
                headers.put(nameStr, value.toString());
              }
            }
          }
        } catch (Exception e) {
          // If we can't handle it as a MultiMap, try as a Map
          if (headersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) headersObj;
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
              if (entry.getKey() != null && entry.getValue() != null) {
                headers.put(entry.getKey().toString(), entry.getValue().toString());
              }
            }
          }
        }
        
        // Extract the context from the headers
        return GlobalOpenTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), headers, GETTER);
      }
    } catch (Exception e) {
      // If we can't extract the context, return null
    }
    
    return null;
  }

  /**
   * Inject the context into a message's headers.
   *
   * @param message the message
   * @param context the context to inject
   */
  private static void injectContextIntoMessage(Object message, Context context) {
    if (message == null) {
      return;
    }

    try {
      // Try to get headers from the message
      Method headersMethod = message.getClass().getMethod("headers");
      Object headersObj = headersMethod.invoke(message);
      
      if (headersObj != null) {
        // Convert headers to a Map<String, String>
        Map<String, String> headers = new HashMap<>();
        
        // Try to handle MultiMap style headers
        try {
          Method namesMethod = headersObj.getClass().getMethod("names");
          Object namesObj = namesMethod.invoke(headersObj);
          
          if (namesObj instanceof Iterable) {
            Method getMethod = headersObj.getClass().getMethod("get", String.class);
            Method setMethod = headersObj.getClass().getMethod("set", String.class, String.class);
            
            // Inject the context into the headers
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, SETTER);
            
            // Set the headers on the message
            for (Map.Entry<String, String> entry : headers.entrySet()) {
              setMethod.invoke(headersObj, entry.getKey(), entry.getValue());
            }
            return;
          }
        } catch (Exception e) {
          // If we can't handle it as a MultiMap, try as a Map
          if (headersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) headersObj;
            
            // Inject the context into the headers
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, SETTER);
            
            // Set the headers on the message
            for (Map.Entry<String, String> entry : headers.entrySet()) {
              map.put(entry.getKey(), entry.getValue());
            }
            return;
          }
        }
      }
    } catch (Exception e) {
      // If we can't inject the context, just return
    }
  }
}
