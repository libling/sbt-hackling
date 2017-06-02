package hackling

import java.io.{BufferedOutputStream, FileOutputStream}

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.RefSpec
import sbt._

import scala.collection.immutable.Seq

object taskImpl {

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

  // TODO some more clever kind of file filter?
  private val SourceFileFilter = new SimpleFileFilter(f => f.isFile && f.name.endsWith(".scala"))

  def installSources(target: File, liblingSubPaths: Seq[String], dependencies: Seq[VersionCached]): Seq[File] = {

    val hashes = dependencies.map(_.version.hash).toSet
    val installed = installedLibs(target).toSet
    val toRemove = (installed -- hashes).map(hash => target / hash)
    IO.delete(toRemove)

    val freshlyInstalled = for {
      VersionCached(VersionHash(hash), local, _) <- dependencies
      installTarget = target / hash
      if !installTarget.exists() // TODO should be verified as installed correctly
      localRepo = Git.open(local)
      objectId = ObjectId.fromString(hash)
      if canResolve(localRepo, objectId)
      installed <- installSource(localRepo, liblingSubPaths, objectId, installTarget)
    } yield installed

    (target ** SourceFileFilter).get.toVector
  }

  /** Currently installed liblings. */
  def installedLibs(base: File) = (base * DirectoryFilter).get.map(_.name)

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

  def updateRepo(repo: Git) = {
    // TODO what if there's other repos than "origin"?
    val heads = new RefSpec("refs/heads/*:refs/remotes/origin/*")
    val tags = new RefSpec("refs/tags/*:refs/remotes/origin/tags/*")
    repo.fetch()
      .setCheckFetchedObjects(true)
      .setRefSpecs(heads,tags)
      .call()
  }

  def downloadGitRepo(local: File)(repo: URI): File = {

    val clone = Git
      .cloneRepository()
      .setURI(repo.toString)
      .setDirectory(local)
      .setBare(true)
      .call()

    assert(local.exists())

    local
  }

  def localRepo(cache:File)(repo: URI): File =
    util.localFile(cache)(repo.toASCIIString).getCanonicalFile

  // needed for installSource
  // maybe implement some archiver to go directly to files?
  org.eclipse.jgit.archive.ArchiveFormats.registerAll()

  /** Install the source for a revision, given by hash string. */
  def installSource(cachedRepo: Git, paths: Seq[String], revision: ObjectId, target: File): Set[File] =
    IO.withTemporaryFile(target.getName,".zip") { tmp =>
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
      Dependency(Version(hash), repo) <- deps
      // TODO some kind of resolve error instead of silent fail
      (local, _) <- findCached(VersionHash(hash), repo.gitRepos).toSeq
      direct = VersionCached(VersionHash(hash), local, repo)
      dep <- direct +: transitive(Git.open(local), ObjectId.fromString(hash))
    } yield dep
  }

  def transitiveResolve(cache: File)(repo: Git, revision: ObjectId): Seq[VersionCached] = {
    IO.withTemporaryFile("dependency-lock",".zip") { tmp =>
      val out = new BufferedOutputStream(new FileOutputStream(tmp))
      repo.archive().setFormat("zip").setTree(revision).setPaths("libling").setOutputStream(out).call

      IO.withTemporaryDirectory { tmpLiblingDir =>
        val files = IO.unzip(tmp, tmpLiblingDir)

        val loadedDeps = for {
          lock <- (files * "lock").get.map(locking.readLock)
          // TODO meta isn't strictly necessary, but for now assume it is
          meta <- (files * "meta").get.map(locking.readMeta)
          loaded <- loadDependencies(cache, lock, meta)
        } yield loaded

        loadedDeps.toVector
      }
    }
  }

  def findCachedRepo(cache: File)(version: VersionHash, repoURIs: Seq[URI]): Option[(File,URI)] = {
    val inCache = cachedRepo(cache) _

    repoURIs
      .toStream // so we only do expensive stuff lazily
      .distinct // and only once
      .map(r => (inCache(r), r))
      .find { case (cached,_) => canResolve(Git.open(cached), ObjectId.fromString(version.hash)) }
  }

  /** Find or fetch locally cached repo. */
  def cachedRepo(cache: File)(repo: URI): File = {
    val local = localRepo(cache)(repo)

    if (local.exists()) local
    else downloadGitRepo(local)(repo)
  }

}
