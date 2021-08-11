plugins {
  maven
  scala
  `java-library`
}

dependencies {
  val v = "2.13"
  implementation("org.scala-lang", "scala-reflect", "$v.6")
  api("io.github.portfoligno.micrixalus", "scala_$v", "1.1.0")
  api("io.github.portfoligno.micrixalus", "scala-predef_$v", "1.1.0")

  implementation("org.typelevel", "literally_$v", "1.0.2")
  implementation("org.http4s", "http4s-blaze-server_$v", "1.0.0-M24")
  implementation("org.http4s", "http4s-blaze-client_$v", "1.0.0-M24")
  api("org.http4s", "http4s-dsl_$v", "1.0.0-M21")
}
