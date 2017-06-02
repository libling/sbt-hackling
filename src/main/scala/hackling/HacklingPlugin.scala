package hackling

import hackling.Hackling._
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import scala.collection.immutable

object HacklingPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val Version = hackling.Version
    val Repositories = hackling.Repositories
    val Dependency = hackling.Dependency

    val sourceDependencies = settingKey[Seq[Dependency]]("git repo dependencies")
    val sourceDependencyBase = settingKey[File]("where source dependencies end up in")
    val liblingCacheDirectory = settingKey[File]("local cache for libling dependency repos")

    val liblingUpdate = taskKey[immutable.Seq[File]]("resolve source dependencies and generate lock file")
    val liblingInstall = taskKey[immutable.Seq[File]]("install source dependencies to source dependency directory")
    val liblingDependencies = taskKey[immutable.Seq[VersionCached]]("load all dependencies as resolved from the lock file and find a cached repo")
  }

  object internal {
    // internal settings
    val liblingMetaDirectory = settingKey[File]("metadata directory for this libling")
    val liblingLockFile = settingKey[File]("libling lock file")
    val liblingMetaFile = settingKey[File]("libling metadata file")
    val liblingPaths = settingKey[immutable.Seq[String]]("paths to checkout for each libling")

    // internal tasks
    val liblingResolve = taskKey[immutable.Seq[VersionCached]]("compute resolved hashes of source dependencies and transitive dependencies")
    val liblingLock = taskKey[Lock]("dependency lock")
    val liblingMeta = taskKey[Meta]("dependency metadata")
  }


  import autoImport._
  import internal._

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
    // consider: should install be doing the update as well?
    liblingInstall := taskImpl.installSources(sourceDependencyBase.value, liblingPaths.value.toVector, liblingDependencies.value),
    liblingResolve := taskImpl.resolve(liblingCacheDirectory.value)(sourceDependencies.value.toVector),

    liblingLock := locking.readLock(liblingLockFile.value),
    liblingMeta := locking.readMeta(liblingMetaFile.value),
    liblingDependencies := taskImpl.loadDependencies(liblingCacheDirectory.value, liblingLock.value, liblingMeta.value),

    sourceGenerators in Compile += liblingInstall.taskValue
  )

}



