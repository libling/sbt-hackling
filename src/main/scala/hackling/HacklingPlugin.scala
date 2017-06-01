package hackling

import java.io.{BufferedOutputStream, FileOutputStream}

import hackling.Hackling._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.RefSpec
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object HacklingPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val Version = Hackling.Version
    val Repository = Hackling.Repository
    val Dependency = Hackling.Dependency

    val sourceDependencies = settingKey[Seq[Dependency]]("git repo dependencies")
    val sourceDependencyBase = settingKey[File]("where source dependencies end up in")
    val liblingCacheDirectory = settingKey[File]("local cache for libling dependency repos")
    val liblingMetaDirectory = settingKey[File]("metadata directory for this libling")
    val liblingPaths = settingKey[Seq[String]]("paths to checkout for each libling")

    // TODO only needed when deps can be refs and not only hashes (YAGNI)
//    val liblingUpdate = taskKey[File]("resolve source dependencies and generate lock file")
    val liblingResolve = taskKey[Seq[VersionCached]]("resolved hashes of source dependencies")
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
    liblingPaths := defaultPaths,

//    liblingUpdate := {
//      val versions = liblingResolve.value
//      val lock = liblingMetaDirectory.value / "lock"
//      IO.touch(lock)
//      lock
//      // TODO actual lock file generation
//    },
    // TODO this should be just reading stuff from the lock file / metadata
    liblingResolve := util.resolve(liblingCacheDirectory.value)(sourceDependencies.value),
    liblingInstall := util.installSources(liblingCacheDirectory.value, sourceDependencyBase.value, liblingPaths.value, liblingResolve.value),
    sourceGenerators in Compile += liblingInstall.taskValue
  )

}

object Hackling {
  // a version is just a hash string for now
  case class Version(hash: String)
  // a repository is one or more git repository urls
  case class Repository(gitRepos: URI*)
  case class Dependency(version: Version, repository: Repository)

  // hashes should probably be handled as some number internally
  case class VersionHash(hash: String)

  // locally cached repo for the version
  case class VersionCached(version: VersionHash, file: File)

  val defaultPaths = Seq("README.md", "doc", "src", "libling")
}

object util {

  // TODO some more clever kind of file filter?
  val SourceFileFilter = new SimpleFileFilter(f => f.isFile && f.name.endsWith(".scala"))

  def installSources(cache: File, target: File, liblingSubPaths: Seq[String], dependencies: Seq[VersionCached]): Seq[File] = {

    val hashes = dependencies.map(_.version.hash).toSet
    val installed = installedLibs(target).toSet
    val toRemove = (installed -- hashes).map(hash => target / hash)
    IO.delete(toRemove)

    val freshlyInstalled = for {
      VersionCached(VersionHash(hash), local) <- dependencies
      installTarget = target / hash
      if !installTarget.exists() // TODO should be verified as installed correctly
      localRepo = Git.open(local)
      objectId = ObjectId.fromString(hash)
      if canResolve(localRepo, objectId)
      installed <- installSource(objectId, localRepo, liblingSubPaths, installTarget)
    } yield installed

    target.**(SourceFileFilter).get
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

  /**
    * Find or fetch locally cached repo.
    */
  def cachedRepo(cache: File)(repo: URI): File = {
    val local = localRepo(cache)(repo)

    if (local.exists()) local
    else downloadGitRepo(local)(repo)
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
  def installSource(revision: ObjectId, cachedRepo: Git, paths: Seq[String], target: File): Set[File] =
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
    val inCache = cachedRepo(cache) _
    for {
      Dependency(Version(hash), repo) <- deps
      // TODO some kind of resolve error instead of silent fail
      local <- repo.gitRepos.iterator.map(inCache).find(cached => canResolve(Git.open(cached), ObjectId.fromString(hash)))
      // TODO transitive resolve
    } yield VersionCached(VersionHash(hash), local)
  }

  private val unsafeChars: Set[Char] = " %$&+,:;=?@<>#".toSet
  // Scala version of http://stackoverflow.com/questions/4571346/how-to-encode-url-to-avoid-special-characters-in-java/4605848#4605848
  // '/' was removed from the unsafe character list
  // TODO this should be a libling ;)
  private def escape(input: String): String = {

    def toHex(ch: Int) =
      (if (ch < 10) '0' + ch else 'A' + ch - 10).toChar

    def isUnsafe(ch: Char) =
      ch > 128 || ch < 0 || unsafeChars(ch)

    input.flatMap {
      case ch if isUnsafe(ch) =>
        "%" + toHex(ch / 16) + toHex(ch % 16)
      case other =>
        other.toString
    }
  }

  // stolen from https://github.com/coursier/coursier/blob/master/cache/src/main/scala/coursier/Cache.scala
  def localFile(cache: File)(url: String): File = {
    val path =
      if (url.startsWith("file:///"))
        url.stripPrefix("file://")
      else if (url.startsWith("file:/"))
        url.stripPrefix("file:")
      else
      // FIXME Should we fully parse the URL here?
      // FIXME Should some safeguards be added against '..' components in paths?
        url.split(":", 2) match {
          case Array(protocol, remaining) =>
            val remaining0 =
              if (remaining.startsWith("///"))
                remaining.stripPrefix("///")
              else if (remaining.startsWith("/"))
                remaining.stripPrefix("/")
              else
                throw new Exception(s"URL $url doesn't contain an absolute path")

            val remaining1 =
              if (remaining0.endsWith("/"))
                // keeping directory content in .directory files
                remaining0 + ".directory"
              else
                remaining0

            new File(
              cache,
              escape(protocol + "/" + remaining1.dropWhile(_ == '/'))
            ).toString

          case _ =>
            throw new Exception(s"No protocol found in URL $url")
        }

    new File(path)
  }

}