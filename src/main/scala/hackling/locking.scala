package hackling
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigValueFactory}
import sbt._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

/** Libling lockfile. Only the current state of the lock file is the source for dependency hashes. */
case class Lock private(hashes: Seq[VersionHash])
object Lock {
  def fromCached(resolved: Seq[VersionCached]): Lock = {
    // TODO add/check hash of hashes before writing anything
    val hashes = resolved.map(_.version).sortBy(_.hash)
    Lock(hashes)
  }
  def fromHashes(hashes: Seq[VersionHash]): Lock = {
    val sortedHashes = hashes.sortBy(_.hash)
    Lock(sortedHashes)
  }
}

/**
  * A list of repos to look up stuff that doesn't have an associated repo
  * and an index where to find hashes.
  * May be updated independently from lock, e.g. to add repos and mappings.
  */
case class Meta private(repos: Seq[URI], index: Map[VersionHash, Repositories]) {
  import Meta._

  def toConfig: Config = {

    val repoList = repos.map(util.normalizedUriString)
    val reposConf = ConfigValueFactory.fromIterable(repoList.asJava)

    val indexMap = index.map {
      case (VersionHash(hash), Repositories(uris)) =>
        (hash, uris.map(util.normalizedUriString).asJava)
    }.asJava

    val indexConf = ConfigValueFactory.fromMap(indexMap)

    ConfigFactory.empty("writeMeta")
      .withValue(REPOS, reposConf)
      .withValue(INDEX, indexConf)
  }
}
object Meta {

  val REPOS = "repos"
  val INDEX = "index"

  def apply(resolved: Seq[VersionCached]): Meta = {
    val repos = for {
      versionCached <- resolved
      repo <- versionCached.origin.gitRepos
      if isOnlineURI(repo)
    } yield repo

    val hashRepo = for {
      VersionCached(hash, _, Repositories(gitRepos)) <- resolved
    } yield {
      val onlineRepos = gitRepos.filter(isOnlineURI).toVector
      (hash, Repositories.fromURIs(onlineRepos))
    }
    // TODO warn when not all hashes have a known online repo

    Meta(repos, hashRepo.toMap)
  }

  def fromConfig(conf: Config): Meta = {

    val repos =
      if(conf.hasPath(REPOS)) conf.getStringList(REPOS).asScala.map(uri).toVector
      else Vector.empty

    val indexConfig =
      if (conf.hasPath(INDEX))
        conf.getConfig(INDEX).entrySet().asScala.toVector
      else Vector.empty

    val index = for {
      entry <- indexConfig
    } yield {
      val hash = entry.getKey
      val repos =
        entry.getValue.unwrapped() //uuurrgh
          .asInstanceOf[java.util.List[String]].asScala
          .map(uri)

      (VersionHash(hash), Repositories.fromURIs(repos.toVector))
    }

    Meta(repos, index.toMap)
  }

  private def isOnlineURI(uri: URI) =
    // TODO find out if somebody has solved this more robustly
    uri.isAbsolute && !uri.getScheme.startsWith("file")
}

object locking {

  val HASHES = "hashes"

  val renderOpts =
    ConfigRenderOptions.defaults
      .setOriginComments(false)
      .setComments(true)
      .setJson(false)
      .setFormatted(true)

  def readLock(lockFile: File): Lock = {
    val lockConf = ConfigFactory.parseFile(lockFile)
    if (lockConf.hasPath(HASHES)) {
      val hashes = lockConf.getStringList(HASHES).asScala.map(VersionHash).sortBy(_.hash)
      Lock.fromHashes(hashes.toVector)
    } else {
      Lock.fromHashes(Vector.empty)
    }
  }

  def writeLock(lockFile: File, lock: Lock) = {
    val stringHashes = lock.hashes.map(_.hash)
    val hashesValue = ConfigValueFactory.fromIterable(stringHashes.asJava)
    val lockConf =
      ConfigFactory.empty("writeLock")
        .withValue(HASHES, hashesValue)

    val rendered = lockConf.root().render(renderOpts)
    IO.write(lockFile, rendered)
  }

  def readMeta(metaFile: File): Meta = {
    val metaConf = ConfigFactory.parseFile(metaFile)
    Meta.fromConfig(metaConf)
  }

  def writeMeta(metaFile: File, meta: Meta) = {
    val metaConf = meta.toConfig
    val rendered = metaConf.root().render(renderOpts)
    IO.write(metaFile, rendered)
  }

}
