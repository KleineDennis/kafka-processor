import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Locale

import sbt.Keys.{javaOptions, _}

organization in ThisBuild := "com.autoscout24"
version in ThisBuild := Option(System.getenv("GO_PIPELINE_LABEL")).getOrElse("1.0-SNAPSHOT")
scalaVersion in ThisBuild := "2.12.2"
scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")

fork in ThisBuild := true

buildInfoKeys ++= Seq[BuildInfoKey](
  BuildInfoKey.action("buildTime") {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", new Locale("en"))
    val timeZone = ZoneId.of("CET") // central european time
    ZonedDateTime.now(timeZone).format(dateTimeFormatter)
  })

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .aggregate(service, localKafka)

lazy val service = (project in file("service"))
  .settings(
    name := "listing-images-kafka-processor",
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "info",
    javaOptions in Test ++= Seq("-Dconfig.resource=test.conf"),
    javaOptions in run ++= Seq(
      "-Dconfig.resource=as24local.conf",
      s"-Dlisting-images.client-secret=${BuildHelper.getListingImagesClientSecret}",
      "-Dlogger.resource=logback-local.xml"
    ),
    libraryDependencies ++=  Seq(
      "log4j" % "log4j" % "1.2.17",
      "com.typesafe.play" %% "play-json" % "2.6.0",
      "org.apache.kafka" % "kafka-streams" % "0.11.0.0",
      "org.apache.kafka" % "kafka-clients" % "0.11.0.0",
      "com.typesafe.akka" %% "akka-http" % "10.0.7",
      "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
      "com.datadoghq" % "java-dogstatsd-client" % "2.3",
      "ch.qos.logback" % "logback-classic" % "1.1.11",
      "net.logstash.logback" % "logstash-logback-encoder" % "4.8",
      "com.typesafe" % "config" % "1.3.1",
      "com.google.guava" % "guava" % "22.0",
      "org.mockito" % "mockito-core" % "2.7.14" % Test,
      "com.miguno.akka" %% "akka-mock-scheduler" % "0.5.1" % Test,
      "org.scalatest" %% "scalatest" % "3.0.3" % Test,
      "org.apache.kafka" %% "kafka" % "0.11.0.0" % Test,
      "net.manub" %% "scalatest-embedded-kafka" % "0.13.1" % Test intransitive()
    ),
    assemblyJarName := s"${name.value}-${version.value}.jar",
    test in assembly := { },
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs@_*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case x => MergeStrategy.first
    }
  )

lazy val localKafka = (project in file("localKafka"))
  .dependsOn(service % "compile->test")
  .settings(
    name := "listing-images-kafka-processor-local-kakfa",
    libraryDependencies ++= Seq()
  )
