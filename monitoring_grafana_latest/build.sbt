version := "1.0"
scalaVersion := "2.13.10"
mainClass := Some("Server")

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.4.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case PathList("application.conf") => MergeStrategy.concat
  case PathList("reference.conf") => MergeStrategy.concat
  case PathList("version.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.github.pureconfig" %% "pureconfig" % "0.17.2",
  "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.0",
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.8.1"
)

libraryDependencies += "io.kamon" %% "kamon-akka" % "1.1.7"

assembly / assemblyJarName := "LogisticDeliverSimulator.jar"
