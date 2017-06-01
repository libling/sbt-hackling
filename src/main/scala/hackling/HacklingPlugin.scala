package hackling

import hackling.Hackling._
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import scala.collection.immutable.Seq

object HacklingPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val Version = hackling.Version
    val Repository = hackling.Repositories
    val Dependency = hackling.Dependency

    val sourceDependencies = settingKey[Seq[Dependency]]("git repo dependencies")
    val sourceDependencyBase = settingKey[File]("where source dependencies end up in")
    val liblingCacheDirectory = settingKey[File]("local cache for libling dependency repos")
    val liblingMetaDirectory = settingKey[File]("metadata directory for this libling")
    val liblingLockFile = settingKey[File]("libling lock file")
    val liblingMetaFile = settingKey[File]("libling metadata file")
    val liblingPaths = settingKey[Seq[String]]("paths to checkout for each libling")

    val liblingUpdate = taskKey[Seq[File]]("resolve source dependencies and generate lock file")
    val liblingResolve = taskKey[Seq[VersionCached]]("compute resolved hashes of source dependencies")
    val liblingInstall = taskKey[Seq[File]]("install source dependencies to source dependency directory")
    val liblingVerify = taskKey[Boolean]("verify that this project is a valid libling")
  }


  import autoImport._

  override lazy val projectSettings = Seq(
    sourceDependencies := Seq.empty,
    sourceDependencyBase := target.value / "libling",

    // only calculated on reload. kind of meh?
    managedSourceDirectories in Compile ++= ((sourceDependencyBase.value ** DirectoryFilter) / "src").get,

    liblingCacheDirectory := file(sys.props.get("user.home").get) / ".libling" / "cache",
    liblingMetaDirectory := baseDirectory.value / "libling",
    liblingLockFile := liblingMetaDirectory.value / "lock",
    liblingMetaFile := liblingMetaDirectory.value / "meta",
    liblingPaths := defaultPaths,

    liblingUpdate := taskImpl.updateLock(liblingLockFile.value, liblingMetaFile.value, liblingResolve.value),
    // TODO this should be just reading stuff from the lock file / metadata
    liblingResolve := taskImpl.resolve(liblingCacheDirectory.value)(sourceDependencies.value),
    liblingInstall := taskImpl.installSources(sourceDependencyBase.value, liblingPaths.value, liblingResolve.value),
    sourceGenerators in Compile += liblingInstall.taskValue
  )

}



