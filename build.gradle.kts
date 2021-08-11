
tasks.wrapper {
  gradleVersion = "6.9"
}

subprojects {
  repositories {
    mavenCentral()
    maven("https://jitpack.io")
  }

  tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf(
        "-Yimports:micrixalus,micrixalus.predef",
        "-language:higherKinds",
        "-language:implicitConversions"
    )
  }
}
