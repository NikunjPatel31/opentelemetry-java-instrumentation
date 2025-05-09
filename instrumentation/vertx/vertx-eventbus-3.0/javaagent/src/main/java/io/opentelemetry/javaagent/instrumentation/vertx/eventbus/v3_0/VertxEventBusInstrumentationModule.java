/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/** Instrumentation module for Vert.x EventBus. */
@AutoService(InstrumentationModule.class)
public class VertxEventBusInstrumentationModule extends InstrumentationModule {

  public VertxEventBusInstrumentationModule() {
    super("vertx-eventbus", "vertx-eventbus-3.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new VertxEventBusInstrumentation());
  }
}
