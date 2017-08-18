val sharedSettings = Seq(
  organization := "libling",
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
    crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.3")
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
