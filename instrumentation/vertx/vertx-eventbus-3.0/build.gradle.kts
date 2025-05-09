plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-core:3.0.0")
}
