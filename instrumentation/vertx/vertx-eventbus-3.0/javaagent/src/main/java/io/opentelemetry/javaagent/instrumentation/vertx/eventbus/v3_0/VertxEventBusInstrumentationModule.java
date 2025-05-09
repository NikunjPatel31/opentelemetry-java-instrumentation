/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This module instruments Vert.x EventBus operations.
 * It traces the flow of messages from sender to consumer.
 */
@AutoService(InstrumentationModule.class)
public class VertxEventBusInstrumentationModule extends InstrumentationModule {

  public VertxEventBusInstrumentationModule() {
    super("vertx-eventbus", "vertx-eventbus-3.0", "vertx");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Only apply this instrumentation if Vert.x EventBus is on the classpath
    return hasClassesNamed("io.vertx.core.eventbus.EventBus");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new EventBusImplInstrumentation(),
        new MessageConsumerImplInstrumentation());
  }
}
