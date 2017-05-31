name := """sbt-hackling"""
organization := "libling"

scalaVersion := "2.10.6"

sbtPlugin := true

libraryDependencies ++= Seq(
  "org.scalactic" %% "scalactic" % "3.0.1" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.typesafe" % "config" % "1.3.1",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.7.0.201704051617-r",
  "org.eclipse.jgit" % "org.eclipse.jgit.archive" % "4.7.0.201704051617-r"
)

//addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")


bintrayPackageLabels := Seq("sbt","plugin")
bintrayVcsUrl := Some("""https://github.com/libling/sbt-hackling.git""")

initialCommands in console :=
  """
    |import hackling._
    |import org.eclipse.jgit.api.Git
    |import scala.collection.JavaConverters._
    |import sbt._
    |""".stripMargin

// set up 'scripted; sbt plugin for testing sbt plugins
ScriptedPlugin.scriptedSettings
scriptedLaunchOpts ++=
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
