import sbt.Keys.licenses
import sbt.ScriptedPlugin.scriptedLaunchOpts

val sharedSettings = Seq(
  organization := "libling",
  scalaVersion := "2.10.6",
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  bintrayVcsUrl := Some("""https://github.com/libling/sbt-hackling.git"""),
  bintrayOmitLicense := false
)

val jgitVersion = "4.7.0.201704051617-r"

initialCommands in console :=
  """
    |import hackling._
    |import org.eclipse.jgit.api.Git
    |import scala.collection.JavaConverters._
    |import sbt._
    |""".stripMargin


val lib = project
  .settings(sharedSettings)
  .settings(
    name := "hackling-lib",
    crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")
  )
val plugins = project
  .dependsOn(lib)
  .settings(sharedSettings)
  .settings(
    name := "sbt-hackling",
    sbtPlugin := true,
    bintrayPackageLabels := Seq("sbt","plugin"),
    bintrayRepository := "sbt-plugins",
    // set up 'scripted; sbt plugin for testing sbt plugins
    ScriptedPlugin.scriptedSettings,
    scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    crossScalaVersions := Seq(scalaVersion.value),
    publishLocal := publishLocal.dependsOn(publishLocal in lib).value,
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % "3.0.1" % "test",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "com.typesafe" % "config" % "1.3.1",
      "org.eclipse.jgit" % "org.eclipse.jgit" % jgitVersion,
      "org.eclipse.jgit" % "org.eclipse.jgit.archive" % jgitVersion,
      "org.slf4j" % "slf4j-simple" % "1.7.25" // just to get rid of annoying warning from jgit including slf4j
    )
  )


// silly bintray plugin doesn't have settings to set these things directly
val bintrayDumpCredentials = taskKey[Boolean]("dump bintray credentials read from environment vars to file. For use in Travis.")
bintrayDumpCredentials := {

  val dumped = for {
    user <- sys.env.get("BINTRAY_USER")
    key <- sys.env.get("BINTRAY_KEY")
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

  assert(bintrayCredentialsFile.value.isFile, s"Bintray credentials not created. Are BINTRAY_USER and BINTRAY_KEY defined?")
  dumped.fold(false)(_ => true)
}
