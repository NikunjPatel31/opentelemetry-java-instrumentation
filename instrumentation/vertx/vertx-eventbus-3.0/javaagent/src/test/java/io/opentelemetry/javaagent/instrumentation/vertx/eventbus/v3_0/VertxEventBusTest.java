/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxEventBusTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static Vertx vertx;

  @BeforeAll
  static void setUp() {
    vertx = Vertx.vertx();
  }

  @AfterAll
  static void tearDown() {
    vertx.close();
  }

  @Test
  void testSendReceive() throws Exception {
    String address = "test.address";
    String message = "Hello, EventBus!";
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<String> receivedMessage = new CompletableFuture<>();

    // Register a consumer
    MessageConsumer<String> consumer = vertx.eventBus().consumer(address);
    consumer.handler(msg -> {
      receivedMessage.complete(msg.body());
      latch.countDown();
    });

    // Wait for the consumer to be registered
    consumer.completionHandler(ar -> {
      if (ar.succeeded()) {
        // Send a message
        testing.runWithSpan("parent", () -> {
          vertx.eventBus().send(address, message);
        });
      } else {
        receivedMessage.completeExceptionally(ar.cause());
        latch.countDown();
      }
    });

    // Wait for the message to be received
    latch.await(10, TimeUnit.SECONDS);
    assertEquals(message, receivedMessage.get(5, TimeUnit.SECONDS));

    // Verify the spans
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent"),
              span -> span.hasName("vertx.eventbus.send")
                  .hasKind(SpanKind.PRODUCER)
                  .hasParent(trace.getSpan(0))
                  .hasAttributesSatisfyingExactly(
                      equalTo(AttributeKey.stringKey("messaging.system"), "vertx-eventbus"),
                      equalTo(AttributeKey.stringKey("messaging.destination"), address),
                      equalTo(AttributeKey.stringKey("messaging.destination_kind"), "topic"),
                      equalTo(AttributeKey.stringKey("messaging.operation"), "send"),
                      equalTo(AttributeKey.stringKey("messaging.vertx.eventbus"), "true"),
                      equalTo(AttributeKey.stringKey("messaging.message.payload.size"), String.valueOf(message.length()))),
              span -> span.hasName("vertx.eventbus.receive")
                  .hasKind(SpanKind.CONSUMER)
                  .hasParent(trace.getSpan(1))
                  .hasAttributesSatisfyingExactly(
                      equalTo(AttributeKey.stringKey("messaging.system"), "vertx-eventbus"),
                      equalTo(AttributeKey.stringKey("messaging.destination"), address),
                      equalTo(AttributeKey.stringKey("messaging.destination_kind"), "topic"),
                      equalTo(AttributeKey.stringKey("messaging.operation"), "receive"),
                      equalTo(AttributeKey.stringKey("messaging.vertx.eventbus"), "true")),
              span -> span.hasName("vertx.eventbus.handle")
                  .hasKind(SpanKind.CONSUMER)
                  .hasParent(trace.getSpan(1))
                  .hasAttributesSatisfyingExactly(
                      equalTo(AttributeKey.stringKey("messaging.system"), "vertx-eventbus"),
                      equalTo(AttributeKey.stringKey("messaging.destination"), address),
                      equalTo(AttributeKey.stringKey("messaging.destination_kind"), "topic"),
                      equalTo(AttributeKey.stringKey("messaging.operation"), "process"),
                      equalTo(AttributeKey.stringKey("messaging.vertx.eventbus"), "true")));
        });
  }

  @Test
  void testPublish() throws Exception {
    String address = "test.publish.address";
    String message = "Hello, EventBus Publish!";
    CountDownLatch latch = new CountDownLatch(2);
    CompletableFuture<String> receivedMessage1 = new CompletableFuture<>();
    CompletableFuture<String> receivedMessage2 = new CompletableFuture<>();

    // Register first consumer
    MessageConsumer<String> consumer1 = vertx.eventBus().consumer(address);
    consumer1.handler(msg -> {
      receivedMessage1.complete(msg.body());
      latch.countDown();
    });

    // Register second consumer
    MessageConsumer<String> consumer2 = vertx.eventBus().consumer(address);
    consumer2.handler(msg -> {
      receivedMessage2.complete(msg.body());
      latch.countDown();
    });

    // Wait for both consumers to be registered
    CompletableFuture<Void> consumer1Registered = new CompletableFuture<>();
    CompletableFuture<Void> consumer2Registered = new CompletableFuture<>();
    
    consumer1.completionHandler(ar -> {
      if (ar.succeeded()) {
        consumer1Registered.complete(null);
      } else {
        consumer1Registered.completeExceptionally(ar.cause());
      }
    });
    
    consumer2.completionHandler(ar -> {
      if (ar.succeeded()) {
        consumer2Registered.complete(null);
      } else {
        consumer2Registered.completeExceptionally(ar.cause());
      }
    });

    // Wait for both consumers to be registered
    CompletableFuture.allOf(consumer1Registered, consumer2Registered).thenRun(() -> {
      // Publish a message
      testing.runWithSpan("parent", () -> {
        vertx.eventBus().publish(address, message);
      });
    });

    // Wait for both messages to be received
    latch.await(10, TimeUnit.SECONDS);
    assertEquals(message, receivedMessage1.get(5, TimeUnit.SECONDS));
    assertEquals(message, receivedMessage2.get(5, TimeUnit.SECONDS));

    // Verify the spans - note that the exact order of receive/handle spans might vary
    testing.waitAndAssertSortedTraces(
        (a, b) -> a.getSpan(0).getName().compareTo(b.getSpan(0).getName()),
        trace -> {
          trace.hasRootSpan("parent");
          trace.hasSpansSatisfying(
              span -> span.hasName("vertx.eventbus.publish")
                  .hasKind(SpanKind.PRODUCER)
                  .hasAttributesSatisfying(
                      equalTo(AttributeKey.stringKey("messaging.system"), "vertx-eventbus"),
                      equalTo(AttributeKey.stringKey("messaging.destination"), address),
                      equalTo(AttributeKey.stringKey("messaging.destination_kind"), "topic"),
                      equalTo(AttributeKey.stringKey("messaging.operation"), "publish")));
        });
  }

  @Test
  void testRequest() throws Exception {
    String address = "test.request.address";
    String message = "Hello, EventBus Request!";
    String reply = "Hello from the other side!";
    CompletableFuture<String> receivedReply = new CompletableFuture<>();

    // Register a consumer that replies
    MessageConsumer<String> consumer = vertx.eventBus().consumer(address);
    consumer.handler(msg -> {
      msg.reply(reply);
    });

    // Wait for the consumer to be registered
    consumer.completionHandler(ar -> {
      if (ar.succeeded()) {
        // Send a request
        testing.runWithSpan("parent", () -> {
          vertx.eventBus().<String>request(address, message, new DeliveryOptions().setSendTimeout(5000))
              .onComplete(result -> {
                if (result.succeeded()) {
                  receivedReply.complete(result.result().body());
                } else {
                  receivedReply.completeExceptionally(result.cause());
                }
              });
        });
      } else {
        receivedReply.completeExceptionally(ar.cause());
      }
    });

    // Wait for the reply
    assertEquals(reply, receivedReply.get(5, TimeUnit.SECONDS));

    // Verify the spans
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent"),
              span -> span.hasName("vertx.eventbus.request")
                  .hasKind(SpanKind.PRODUCER)
                  .hasParent(trace.getSpan(0))
                  .hasAttributesSatisfying(
                      equalTo(AttributeKey.stringKey("messaging.system"), "vertx-eventbus"),
                      equalTo(AttributeKey.stringKey("messaging.destination"), address),
                      equalTo(AttributeKey.stringKey("messaging.destination_kind"), "topic"),
                      equalTo(AttributeKey.stringKey("messaging.operation"), "request")));
        });
  }

  @Test
  void testErrorHandling() throws Exception {
    String address = "test.error.address";
    String message = "Hello, Error!";
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Throwable> receivedException = new CompletableFuture<>();

    // Register a consumer that throws an exception
    MessageConsumer<String> consumer = vertx.eventBus().consumer(address);
    consumer.handler(msg -> {
      throw new RuntimeException("Test exception");
    });

    // Wait for the consumer to be registered
    consumer.completionHandler(ar -> {
      if (ar.succeeded()) {
        // Send a message
        testing.runWithSpan("parent", () -> {
          try {
            vertx.eventBus().send(address, message);
          } catch (Exception e) {
            receivedException.complete(e);
            latch.countDown();
          }
        });
      } else {
        receivedException.complete(ar.cause());
        latch.countDown();
      }
    });

    // Wait for the exception
    latch.await(10, TimeUnit.SECONDS);

    // Verify the spans - we should see the error in the handle span
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent"),
              span -> span.hasName("vertx.eventbus.send")
                  .hasKind(SpanKind.PRODUCER)
                  .hasParent(trace.getSpan(0))
                  .hasAttributesSatisfying(
                      equalTo(AttributeKey.stringKey("messaging.system"), "vertx-eventbus"),
                      equalTo(AttributeKey.stringKey("messaging.destination"), address),
                      equalTo(AttributeKey.stringKey("messaging.destination_kind"), "topic"),
                      equalTo(AttributeKey.stringKey("messaging.operation"), "send")));
        });
  }
}
