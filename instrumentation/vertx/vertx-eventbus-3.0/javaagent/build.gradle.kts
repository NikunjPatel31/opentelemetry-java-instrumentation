plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[3.0.0,4.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":javaagent-bootstrap"))
  implementation(project(":javaagent-extension-api"))

  library("io.vertx:vertx-core:3.0.0")
  
  // vertx-codegen and vertx-docgen dependencies are needed for Xlint's annotation checking
  library("io.vertx:vertx-codegen:3.0.0")
  testLibrary("io.vertx:vertx-docgen:3.0.0")

  testImplementation("io.vertx:vertx-core:3.0.0")
  testImplementation("io.vertx:vertx-web:3.0.0")
  
  // We need both version as different versions of Vert.x use different versions of Netty
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}
