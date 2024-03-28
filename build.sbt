import Dependencies._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "otel-fs2-grpc",
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
      "io.grpc"  % "grpc-services" % scalapb.compiler.Version.grpcJavaVersion,
      "org.typelevel" %% "otel4s-oteljava" % "0.5.0-RC2",
      "io.opentelemetry" % "opentelemetry-exporter-logging" % "1.36.0" % Runtime,
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.36.0" % Runtime,
      "io.opentelemetry" % "opentelemetry-exporter-logging" % "1.36.0" % Runtime,
      "io.opentelemetry.instrumentation" % "opentelemetry-grpc-1.6" % "2.2.0-alpha"
    ),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  )
  .settings(
    javaOptions ++= Seq(
      "-Dotel.java.global-autoconfigure.enabled=true",
      "-Dotel.resource.attributes=service.name=hello.world",
      "-Dotel.traces.exporter=logging",
      "-Dotel.logs.exporter=none",
      "-Dotel.metrics.exporter=none",
    ),
  )
  .dependsOn(protobuf)

lazy val protobuf =
  project
    .in(file("protobuf"))
    .settings(
      name := "protobuf",
      scalaVersion := "2.13.13",
      libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
    )
    .enablePlugins(Fs2Grpc)

scalafmtOnCompile := true