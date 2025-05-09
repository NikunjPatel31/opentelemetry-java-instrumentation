plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:vertx:vertx-eventbus-3.0"))
}
