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

// silly bintray plugin doesn't have settings to set these things directly
val bintrayDumpCredentials = taskKey[Boolean]("dump bintray credentials read from environment vars to file. For use in Travis")
bintrayDumpCredentials := {
  val dumped = for {
    user <- sys.env.get("BINTRAY_USER")
    key <- sys.env.get("BINTRAY_KEY")
  } yield {
    val credentials =
      s"""
        |realm = Bintray API Realm
        |host = api.bintray.com
        |user = $user
        |password = $key
      """.stripMargin

    IO.write(bintrayCredentialsFile.value, credentials)
  }

  dumped.fold(false)(_ => true)
}
