name := "scala-messenger-bot"
organization := "com.cpuheater"
version := "0.0.1"

scalaVersion in ThisBuild := "2.12.8"


resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"


libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.10",
  "com.typesafe.akka" %% "akka-actor" % "2.5.24",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe.akka" %% "akka-slf4j" % "2.5.24",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-http" % "10.1.9",
  "com.typesafe.akka" %% "akka-stream" % "2.5.24",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.9"
)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

enablePlugins(AshScriptPlugin)

dockerExposedPorts := Seq(8080)

dockerBaseImage := "openjdk:jre-alpine"

