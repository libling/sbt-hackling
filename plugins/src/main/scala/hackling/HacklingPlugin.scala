package hackling

import hackling.Hackling._
import hackling.internal.{Lock, Meta, locking, taskImpl}
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import scala.collection.immutable

object HacklingPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    type Version = hackling.Version
    val HashVersion = hackling.HashVersion
    val NameVersion = hackling.NameVersion

    val Repositories = hackling.Repositories
    val Dependency = hackling.Dependency

    val sourceDependencies = settingKey[Seq[Dependency]]("git repo dependencies")
    val sourceDependencyBase = settingKey[File]("where source dependencies end up in")
    val liblingCacheDirectory = settingKey[File]("local cache for libling dependency repos")

    val liblingInstall = taskKey[immutable.Seq[File]]("install source dependencies to source dependency directory")
    val liblingDependencies = taskKey[immutable.Seq[VersionCached]]("load all dependencies as resolved from the lock file and find a cached repo")
  }

  object internal {
    import KeyRanks.Invisible
    // internal settings
    val liblingMetaDirectory = SettingKey[File]("liblingMetaDirectory", "metadata directory for this libling", Invisible)
    val liblingLockFile = SettingKey[File]("liblingLockFile", "lock file for this libling", Invisible)
    val liblingMetaFile = SettingKey[File]("liblingMetaFile", "metadata file for this libling", Invisible)
    val liblingPaths = SettingKey[immutable.Seq[String]]("liblingPaths", "paths to checkout for each libling", Invisible)

    // internal tasks
    val liblingUpdate = TaskKey[immutable.Seq[File]]("liblingUpdate","resolve source dependencies and generate lock file", Invisible)
    val liblingResolve = TaskKey[immutable.Seq[VersionCached]]("liblingResolve","compute resolved hashes of source dependencies and transitive dependencies", Invisible)
    val liblingLock = TaskKey[Lock]("liblingLock", "dependency lock", Invisible)
    val liblingMeta = TaskKey[Meta]("liblingMeta", "dependency metadata", Invisible)
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
    liblingInstall := taskImpl.installSources(
      sourceDependencyBase.value,
      liblingPaths.value.toVector,
      liblingDependencies.dependsOn(liblingUpdate).value),
    liblingResolve := taskImpl.resolve(liblingCacheDirectory.value)(sourceDependencies.value.toVector),

    liblingLock := locking.readLock(liblingLockFile.value),
    liblingMeta := locking.readMeta(liblingMetaFile.value),
    liblingDependencies := taskImpl.loadDependencies(liblingCacheDirectory.value, liblingLock.value, liblingMeta.value),

    sourceGenerators in Compile += liblingInstall.taskValue
  )

}
