/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation for the EventBusImpl class to trace send and publish operations.
 */
public class EventBusImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.core.eventbus.impl.EventBusImpl");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.core.eventbus.EventBus"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Instrument send method with DeliveryOptions
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("send"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(2, named("io.vertx.core.eventbus.DeliveryOptions"))),
        this.getClass().getName() + "$SendAdvice");

    // Instrument publish method with DeliveryOptions
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("publish"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(2, named("io.vertx.core.eventbus.DeliveryOptions"))),
        this.getClass().getName() + "$PublishAdvice");

    // Instrument request method
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("request"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(2, named("io.vertx.core.eventbus.DeliveryOptions"))),
        this.getClass().getName() + "$RequestAdvice");
  }

  /**
   * Advice for the send method.
   */
  @SuppressWarnings("unused")
  public static class SendAdvice {
    @net.bytebuddy.asm.Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @net.bytebuddy.asm.Advice.Argument(0) String address,
        @net.bytebuddy.asm.Advice.Argument(1) Object message,
        @net.bytebuddy.asm.Advice.Argument(2) Object deliveryOptions,
        @net.bytebuddy.asm.Advice.Local("otelContext") io.opentelemetry.context.Context context,
        @net.bytebuddy.asm.Advice.Local("otelScope") io.opentelemetry.context.Scope scope) {
      
      context = VertxEventBusTracer.startSend(address, message, "send");
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @net.bytebuddy.asm.Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @net.bytebuddy.asm.Advice.Thrown Throwable throwable,
        @net.bytebuddy.asm.Advice.Local("otelContext") io.opentelemetry.context.Context context,
        @net.bytebuddy.asm.Advice.Local("otelScope") io.opentelemetry.context.Scope scope) {
      
      if (scope != null) {
        scope.close();
        VertxEventBusTracer.end(context, throwable);
      }
    }
  }

  /**
   * Advice for the publish method.
   */
  @SuppressWarnings("unused")
  public static class PublishAdvice {
    @net.bytebuddy.asm.Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @net.bytebuddy.asm.Advice.Argument(0) String address,
        @net.bytebuddy.asm.Advice.Argument(1) Object message,
        @net.bytebuddy.asm.Advice.Argument(2) Object deliveryOptions,
        @net.bytebuddy.asm.Advice.Local("otelContext") io.opentelemetry.context.Context context,
        @net.bytebuddy.asm.Advice.Local("otelScope") io.opentelemetry.context.Scope scope) {
      
      context = VertxEventBusTracer.startSend(address, message, "publish");
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @net.bytebuddy.asm.Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @net.bytebuddy.asm.Advice.Thrown Throwable throwable,
        @net.bytebuddy.asm.Advice.Local("otelContext") io.opentelemetry.context.Context context,
        @net.bytebuddy.asm.Advice.Local("otelScope") io.opentelemetry.context.Scope scope) {
      
      if (scope != null) {
        scope.close();
        VertxEventBusTracer.end(context, throwable);
      }
    }
  }

  /**
   * Advice for the request method.
   */
  @SuppressWarnings("unused")
  public static class RequestAdvice {
    @net.bytebuddy.asm.Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @net.bytebuddy.asm.Advice.Argument(0) String address,
        @net.bytebuddy.asm.Advice.Argument(1) Object message,
        @net.bytebuddy.asm.Advice.Argument(2) Object deliveryOptions,
        @net.bytebuddy.asm.Advice.Local("otelContext") io.opentelemetry.context.Context context,
        @net.bytebuddy.asm.Advice.Local("otelScope") io.opentelemetry.context.Scope scope) {
      
      context = VertxEventBusTracer.startSend(address, message, "request");
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @net.bytebuddy.asm.Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @net.bytebuddy.asm.Advice.Thrown Throwable throwable,
        @net.bytebuddy.asm.Advice.Local("otelContext") io.opentelemetry.context.Context context,
        @net.bytebuddy.asm.Advice.Local("otelScope") io.opentelemetry.context.Scope scope) {
      
      if (scope != null) {
        scope.close();
        VertxEventBusTracer.end(context, throwable);
      }
    }
  }
}
