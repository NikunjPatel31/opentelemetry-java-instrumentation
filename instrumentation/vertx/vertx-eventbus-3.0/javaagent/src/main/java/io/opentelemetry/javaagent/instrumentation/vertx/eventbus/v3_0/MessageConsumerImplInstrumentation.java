/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.eventbus.Message;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrumentation for Vert.x MessageConsumerImpl class. */
public class MessageConsumerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.core.eventbus.impl.MessageConsumerImpl");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.eventbus.impl.MessageConsumerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Instrument the deliver method which is called when a message is received
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("deliver"))
            .and(takesArgument(0, named("io.vertx.core.eventbus.Message"))),
        this.getClass().getName() + "$DeliverAdvice");
  }

  /** Advice for instrumenting MessageConsumerImpl.deliver() method. */
  @SuppressWarnings("unused")
  public static class DeliverAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Message<?> message,
        @Advice.Local("otelReceiveContext") Context receiveContext,
        @Advice.Local("otelReceiveScope") Scope receiveScope) {
      // Create a span for receiving the message
      receiveContext = VertxEventBusTracer.startReceive(message);
      receiveScope = receiveContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Message<?> message,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelReceiveContext") Context receiveContext,
        @Advice.Local("otelReceiveScope") Scope receiveScope) {
      if (receiveScope != null) {
        receiveScope.close();
        VertxEventBusTracer.end(receiveContext, throwable);
      }

      // Create a span for handling the message
      Context handleContext = VertxEventBusTracer.startHandle(message);
      Scope handleScope = handleContext.makeCurrent();
      try {
        // The handler will be called after this advice, so we don't need to do anything here
      } finally {
        handleScope.close();
        VertxEventBusTracer.end(handleContext, throwable);
      }
    }
  }
}
