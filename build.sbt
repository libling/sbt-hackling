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

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

bintrayPackageLabels := Seq("sbt","plugin")
bintrayVcsUrl := Some("""https://github.com/libling/sbt-hackling.git""")
bintrayRepository := "sbt-plugins"
bintrayOmitLicense := false

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
val bintrayDumpCredentials = taskKey[Boolean]("dump bintray credentials read from environment vars to file. For use in Travis.")
bintrayDumpCredentials := {

  val userKey = "BINTRAY_USER"
  val keyKey = "BINTRAY_KEY"

  val dumped = for {
    user <- sys.env.get(userKey)
    key <- sys.env.get(keyKey)
    if !bintrayCredentialsFile.value.isFile
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

  assert(
    bintrayCredentialsFile.value.isFile,
    s"bintray credentials not created. user:${sys.env.get(userKey).isDefined}, key:${sys.env.get(keyKey).isDefined}")
  dumped.fold(false)(_ => true)

}
