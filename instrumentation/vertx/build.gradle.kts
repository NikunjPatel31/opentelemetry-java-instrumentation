plugins {
  id("otel.instrumentation-conventions")
}

subprojects {
  val projectPath = path
  if (projectPath.endsWith("-testing")) {
    plugins.apply("otel.javaagent-testing")
  } else if (projectPath.contains("-shaded-")) {
    plugins.apply("otel.javaagent-instrumentation")
  } else if (projectPath.endsWith("-common")) {
    plugins.apply("otel.javaagent-instrumentation")
  } else if (projectPath.endsWith("-library")) {
    plugins.apply("otel.library-instrumentation")
  } else if (projectPath.endsWith("-javaagent")) {
    plugins.apply("otel.javaagent-instrumentation")
  } else {
    plugins.apply("otel.instrumentation-conventions")
  }
}
