package hackling
package internal

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.net.URI

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.RefSpec
import sbt._
import hackling.internal.util._

import scala.collection.immutable.Seq

private[hackling] object taskImpl {

  /** Load dependencies from lock with help from metadata. */
  def loadDependencies(cache: File, lock: Lock, meta: Meta): Seq[VersionCached] = {
    val findCached = findCachedRepo(cache) _

    for {
      version <- lock.hashes
      repos = meta.index.get(version).map(_.gitRepos).getOrElse(Seq.empty)
      // add all the meta.repos to lookup in case there's some orphan hash
      lookup = repos ++ meta.repos
      (cached,origin) <- findCached(version, lookup)
    } yield {
      // add resolved origin to the front because it's a place we're going to look up in cache first
      val resultRepos = (origin +: repos).distinct
      VersionCached(version, cached, Repositories.fromURIs(resultRepos))
    }
  }

  def updateLock(lockFile: File, metaFile: File, versions: Seq[VersionCached]): Seq[File] = {
    val locky = Lock.fromCached(versions)
    val meta = Meta(versions)
    locking.writeLock(lockFile, locky)
    locking.writeMeta(metaFile, meta)
    Seq(lockFile, metaFile)
  }

  private val ScalaFilter = new SimpleFileFilter(f => f.isFile && f.getName.endsWith(".scala"))

  def installSources(target: File, liblingSubPaths: Seq[String], dependencies: Seq[VersionCached]): Seq[File] = {

    val hashes = dependencies.map(_.version.hash).toSet
    val installed = installedLibs(target).toSet
    val toRemove = (installed -- hashes).map(hash => target / hash)

    for {
      VersionCached(HashVersion(hash), local, _) <- dependencies
      installTarget = target / hash
      if !installTarget.isDirectory
      installed <- withRepo(local) { localRepo =>
        val objectId = ObjectId.fromString(hash)
        if (canResolve(localRepo, objectId))
          installSource(localRepo, liblingSubPaths, objectId, installTarget)
        else Set.empty[File]
      }
    } yield installed

    IO.delete(toRemove)

    (target ** ScalaFilter).get.toVector
  }

  /** Currently installed liblings. */
  private[hackling] def installedLibs(base: File) =
    (base * DirectoryFilter).get.map(_.getName)

  def canResolve(repo: Git, revision: ObjectId): Boolean = {

    def checkIfRevisionExists = try {
      repo.log().add(revision).setMaxCount(1).call.iterator().hasNext
    } catch {
      case _: MissingObjectException => false
    }

    checkIfRevisionExists || {
      updateRepo(repo)
      // try again after a repo update
      checkIfRevisionExists
    }
  }

  def updateRepo(repo: Git): Unit = {
    // TODO what if there's other repos than "origin"?
    val heads = new RefSpec("refs/heads/*:refs/remotes/origin/*")
    val tags = new RefSpec("refs/tags/*:refs/remotes/origin/tags/*")
    repo.fetch()
      .setCheckFetchedObjects(true)
      .setRefSpecs(heads,tags)
      .call()

    repo.close()
  }

  def downloadGitRepo(local: File)(repo: URI): File = {

    val clone = Git
      .cloneRepository()
      .setURI(repo.toString)
      .setDirectory(local)
      .setBare(true)
      .call()

    assert(local.exists())
    clone.close()

    local
  }

  def localRepo(cache:File)(repo: URI): File =
    util.localFile(cache)(repo.toASCIIString).getCanonicalFile

  // needed for installSource
  // maybe implement some archiver to go directly to files?
  org.eclipse.jgit.archive.ArchiveFormats.registerAll()

  /** Install the source for a revision, given by hash string. */
  private[hackling] def installSource(cachedRepo: Git, paths: Seq[String], revision: ObjectId, target: File): Set[File] =
    IO.withTemporaryFile(target.getName, ".zip") { tmp =>
      val out = new BufferedOutputStream(new FileOutputStream(tmp))

      cachedRepo
        .archive()
        .setFormat("zip")
        .setTree(revision)
        .setPaths(paths :_*)
        .setOutputStream(out)
        .call()

      IO.unzip(tmp, target)
    }

  def resolve(cache: File)(deps: Seq[Dependency]): Seq[VersionCached] = {
    // TODO when resolving tags or branches, how to give preference to repos?
    // TagVersion could explicitly define git uri
    val findCached = findCachedRepo(cache) _
    val transitive = transitiveResolve(cache) _
    for {
      Dependency(HashVersion(hash), repo) <- deps
      // TODO some kind of resolve error instead of silent fail
      (local, _) <- findCached(HashVersion(hash), repo.gitRepos).toSeq
      direct = VersionCached(HashVersion(hash), local, repo)
      dep <- direct +: withRepo(local) { git => transitive(git, ObjectId.fromString(hash)) }
    } yield dep
  }

  def transitiveResolve(cache: File)(repo: Git, revision: ObjectId): Seq[VersionCached] = {
    IO.withTemporaryFile("dependency-lock",".zip") { tmp =>
      val out = new BufferedOutputStream(new FileOutputStream(tmp))
      repo.archive().setFormat("zip").setTree(revision).setPaths("libling").setOutputStream(out).call

      IO.withTemporaryDirectory { tmpLiblingDir =>
        val files = IO.unzip(tmp, tmpLiblingDir)

        val loadedDeps = for {
          lock <- (files find (_.getName == "lock")).map(locking.readLock)
          // TODO meta isn't strictly necessary, but for now assume it is
          meta <- (files find (_.getName == "meta")).map(locking.readMeta)
        } yield loadDependencies(cache, lock, meta)

        loadedDeps.getOrElse(Seq.empty)
      }
    }
  }

  def findCachedRepo(cache: File)(version: HashVersion, repoURIs: Seq[URI]): Option[(File,URI)] = {
    val inCache = cachedRepo(cache) _

    repoURIs
      .toStream // expensive stuff should be done lazily
      .distinct // and only once
      .map(r => (inCache(r), r))
      .find { case (cached,_) =>
        withRepo(cached) { git =>
          canResolve(git, ObjectId.fromString(version.hash))
        }
      }
  }

  /** Find or fetch locally cached repo. */
  def cachedRepo(cache: File)(repo: URI): File = {
    val local = localRepo(cache)(repo)

    if (local.exists()) local // TODO check for validity or something
    else downloadGitRepo(local)(repo)
  }

}
