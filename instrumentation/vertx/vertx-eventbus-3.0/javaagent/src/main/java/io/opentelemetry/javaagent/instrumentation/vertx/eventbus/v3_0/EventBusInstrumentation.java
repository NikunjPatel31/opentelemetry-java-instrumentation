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

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrumentation for Vert.x EventBus operations. */
public class EventBusInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.core.eventbus.EventBus");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.core.eventbus.EventBus"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Instrument send operations
    transformer.applyAdviceToMethod(
        isMethod().and(named("send")).and(takesArguments(2)).and(takesArgument(0, String.class)),
        this.getClass().getName() + "$SendAdvice");

    // Instrument publish operations
    transformer.applyAdviceToMethod(
        isMethod().and(named("publish")).and(takesArguments(2)).and(takesArgument(0, String.class)),
        this.getClass().getName() + "$PublishAdvice");

    // Instrument consumer registration
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("consumer"))
            .and(takesArguments(2))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("io.vertx.core.Handler"))),
        this.getClass().getName() + "$ConsumerAdvice");
  }

  /** Advice for instrumenting EventBus.send() methods. */
  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String address,
        @Advice.Argument(1) Object message,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Context.current();
      context = VertxEventBusTracer.startSend(address, message, "send");
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
        VertxEventBusTracer.end(context, throwable);
      }
    }
  }

  /** Advice for instrumenting EventBus.publish() methods. */
  @SuppressWarnings("unused")
  public static class PublishAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String address,
        @Advice.Argument(1) Object message,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Context.current();
      context = VertxEventBusTracer.startSend(address, message, "publish");
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
        VertxEventBusTracer.end(context, throwable);
      }
    }
  }

  /** Advice for instrumenting EventBus.consumer() methods. */
  @SuppressWarnings("unused")
  public static class ConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 1, readOnly = false) Handler<Message<?>> handler) {
      if (handler != null) {
        handler = new TracingMessageHandler(handler);
      }
    }
  }

  /** Wrapper for message handlers to add tracing. */
  public static class TracingMessageHandler implements Handler<Message<?>> {
    private final Handler<Message<?>> delegate;

    public TracingMessageHandler(Handler<Message<?>> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void handle(Message<?> message) {
      // Create a span for receiving the message
      Context receiveContext = VertxEventBusTracer.startReceive(message);
      Scope receiveScope = receiveContext.makeCurrent();
      try {
        // End the receive span before starting the handle span
        receiveScope.close();
        VertxEventBusTracer.end(receiveContext, null);

        // Create a span for handling the message
        Context handleContext = VertxEventBusTracer.startHandle(message);
        Scope handleScope = handleContext.makeCurrent();
        try {
          // Call the original handler
          delegate.handle(message);
        } catch (Throwable t) {
          VertxEventBusTracer.end(handleContext, t);
          throw t;
        } finally {
          handleScope.close();
          VertxEventBusTracer.end(handleContext, null);
        }
      } catch (Throwable t) {
        VertxEventBusTracer.end(receiveContext, t);
        throw t;
      }
    }
  }
}
