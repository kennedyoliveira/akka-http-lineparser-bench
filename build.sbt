val commonSettings = Seq(
  name := "akka-http-lineparser-bench",
  version := "0.1",
  scalaVersion := "2.12.4"
)

val `akka-http-lineparser-bench` = (project in file("."))
  .settings(commonSettings)
  .settings(libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.10")
  .enablePlugins(JmhPlugin)