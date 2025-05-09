/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxEventBusTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private Vertx vertx;
  private EventBus eventBus;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    eventBus = vertx.eventBus();
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void testSendReceive() throws Exception {
    String address = "test-address";
    String message = "Hello, Vert.x EventBus!";

    CompletableFuture<Message<String>> receivedMessageFuture = new CompletableFuture<>();

    testing.runWithSpan(
        "consumer-registration",
        () -> {
          eventBus.consumer(
              address,
              event -> {
                testing.runWithSpan(
                    "message-processing",
                    () -> {
                      receivedMessageFuture.complete(event);
                    });
              });
        });

    testing.runWithSpan(
        "producer",
        () -> {
          eventBus.send(address, message);
        });

    Message<String> receivedMessage = receivedMessageFuture.get(10, TimeUnit.SECONDS);
    assertThat(receivedMessage.body()).isEqualTo(message);

    // Wait for all spans to be reported
    await().atMost(10, TimeUnit.SECONDS).until(() -> testing.spans().size() >= 4);

    // Verify producer span
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span -> {
                span.hasName("vertx.eventbus.send")
                    .hasKind(SpanKind.PRODUCER)
                    .hasParent(trace.getSpan(0))
                    .hasAttributesSatisfyingExactly(
                        equalTo(AttributeKey.stringKey("messaging.system"), "vertx-eventbus"),
                        equalTo(AttributeKey.stringKey("messaging.destination"), address),
                        equalTo(AttributeKey.stringKey("messaging.destination_kind"), "topic"),
                        equalTo(AttributeKey.stringKey("messaging.operation"), "send"),
                        equalTo(AttributeKey.booleanKey("messaging.vertx.eventbus"), true),
                        equalTo(
                            AttributeKey.longKey("messaging.message.payload.size"),
                            (long) message.length()));
              });

          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("consumer-registration"),
              span -> {
                span.hasName("vertx.eventbus.receive")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(0));
              },
              span -> {
                span.hasName("vertx.eventbus.process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(1));
              },
              span -> {
                span.hasName("message-processing").hasParent(trace.getSpan(2));
              });
        });
  }

  @Test
  void testPublishSubscribe() throws Exception {
    String address = "test-publish-address";
    String message = "Hello, Vert.x EventBus Publish!";

    CompletableFuture<Message<String>> receivedMessageFuture = new CompletableFuture<>();

    testing.runWithSpan(
        "consumer-registration",
        () -> {
          eventBus.consumer(
              address,
              event -> {
                testing.runWithSpan(
                    "message-processing",
                    () -> {
                      receivedMessageFuture.complete(event);
                    });
              });
        });

    testing.runWithSpan(
        "producer",
        () -> {
          eventBus.publish(address, message);
        });

    Message<String> receivedMessage = receivedMessageFuture.get(10, TimeUnit.SECONDS);
    assertThat(receivedMessage.body()).isEqualTo(message);

    // Wait for all spans to be reported
    await().atMost(10, TimeUnit.SECONDS).until(() -> testing.spans().size() >= 4);

    // Verify producer span
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span -> {
                span.hasName("vertx.eventbus.publish")
                    .hasKind(SpanKind.PRODUCER)
                    .hasParent(trace.getSpan(0))
                    .hasAttributesSatisfyingExactly(
                        equalTo(AttributeKey.stringKey("messaging.system"), "vertx-eventbus"),
                        equalTo(AttributeKey.stringKey("messaging.destination"), address),
                        equalTo(AttributeKey.stringKey("messaging.destination_kind"), "topic"),
                        equalTo(AttributeKey.stringKey("messaging.operation"), "publish"),
                        equalTo(AttributeKey.booleanKey("messaging.vertx.eventbus"), true),
                        equalTo(
                            AttributeKey.longKey("messaging.message.payload.size"),
                            (long) message.length()));
              });

          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("consumer-registration"),
              span -> {
                span.hasName("vertx.eventbus.receive")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(0));
              },
              span -> {
                span.hasName("vertx.eventbus.process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(1));
              },
              span -> {
                span.hasName("message-processing").hasParent(trace.getSpan(2));
              });
        });
  }

  @Test
  void testConsumerError() throws Exception {
    String address = "test-error-address";
    String message = "Error message";
    RuntimeException testException = new RuntimeException("Test exception");

    CompletableFuture<Throwable> exceptionFuture = new CompletableFuture<>();

    testing.runWithSpan(
        "consumer-registration",
        () -> {
          eventBus.consumer(
              address,
              event -> {
                testing.runWithSpan(
                    "message-processing",
                    () -> {
                      try {
                        throw testException;
                      } catch (Throwable t) {
                        exceptionFuture.complete(t);
                        throw t;
                      }
                    });
              });
        });

    testing.runWithSpan(
        "producer",
        () -> {
          eventBus.send(address, message);
        });

    Throwable exception = exceptionFuture.get(10, TimeUnit.SECONDS);
    assertThat(exception).isEqualTo(testException);

    // Wait for all spans to be reported
    await().atMost(10, TimeUnit.SECONDS).until(() -> testing.spans().size() >= 4);

    // Verify spans with error
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span -> {
                span.hasName("vertx.eventbus.send")
                    .hasKind(SpanKind.PRODUCER)
                    .hasParent(trace.getSpan(0));
              });

          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("consumer-registration"),
              span -> {
                span.hasName("vertx.eventbus.receive")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(0));
              },
              span -> {
                span.hasName("vertx.eventbus.process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(1))
                    .hasStatus(StatusData.error());
              },
              span -> {
                span.hasName("message-processing")
                    .hasParent(trace.getSpan(2))
                    .hasStatus(StatusData.error())
                    .hasException(testException);
              });
        });
  }
}
