/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation for the MessageConsumerImpl class to trace message reception.
 */
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
    // Instrument the doReceive method which is called when a message is received
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("doReceive"))
            .and(takesArgument(0, named("io.vertx.core.eventbus.Message"))),
        this.getClass().getName() + "$DoReceiveAdvice");

    // Instrument the dispatch method which is called to dispatch the message to the handler
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("dispatch"))
            .and(takesArgument(0, named("io.vertx.core.eventbus.Message")))
            .and(takesArgument(2, named("io.vertx.core.Handler"))),
        this.getClass().getName() + "$DispatchAdvice");
  }

  /**
   * Advice for the doReceive method.
   */
  @SuppressWarnings("unused")
  public static class DoReceiveAdvice {
    @net.bytebuddy.asm.Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @net.bytebuddy.asm.Advice.Argument(0) Object message,
        @net.bytebuddy.asm.Advice.Local("otelContext") io.opentelemetry.context.Context context,
        @net.bytebuddy.asm.Advice.Local("otelScope") io.opentelemetry.context.Scope scope) {
      
      // Extract the context from the message and start a new span for receiving
      context = VertxEventBusTracer.startReceive(message);
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
   * Advice for the dispatch method.
   */
  @SuppressWarnings("unused")
  public static class DispatchAdvice {
    @net.bytebuddy.asm.Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @net.bytebuddy.asm.Advice.Argument(0) Object message,
        @net.bytebuddy.asm.Advice.Local("otelContext") io.opentelemetry.context.Context context,
        @net.bytebuddy.asm.Advice.Local("otelScope") io.opentelemetry.context.Scope scope) {
      
      // Extract the context from the message and start a new span for handling
      context = VertxEventBusTracer.startHandle(message);
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
