package hackling

import java.io.{BufferedOutputStream, FileOutputStream}

import sbt.plugins.JvmPlugin
import sbt._
import sbt.Keys._
import Hackling._
import org.eclipse.jgit.api.{ArchiveCommand, Git}
import org.eclipse.jgit.lib.ObjectId

object HacklingPlugin extends AutoPlugin {


  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val sourceDependencies = settingKey[Seq[Dependency]]("git repo dependencies")
    val sourceDependencyBaseDirectory = settingKey[File]("where source dependencies end up in")
    val liblingCache = settingKey[File]("local cache for libling dependency repos")

    val liblingResolve = taskKey[Seq[VersionHash]]("resolved hashes of source dependencies")
    val liblingUpdate = taskKey[Seq[File]]("resolve source dependencies and generate lock file")
    val liblingInstall = taskKey[Seq[File]]("resolve and install source dependencies to source dependency directory")
    val liblingVerify = taskKey[Boolean]("verify that this project is a valid libling")
  }


  import autoImport._

  override lazy val projectSettings = Seq(
    sourceDependencies := Seq.empty,
    sourceDependencyBaseDirectory := sourceDirectory.value / "libling",
    liblingCache := file(sys.props.get("user.home").get) / ".libling" / "cache",

    liblingResolve := Seq.empty,
    liblingUpdate := Seq.empty,
    liblingInstall := Seq.empty
  )

  override lazy val buildSettings = Seq()

  override lazy val globalSettings = Seq()
}

object Hackling {
  // a version is just a hash string for now
  case class Version(hash: String)
  case class Repository(gitRepo: URI*)
  case class Dependency(version: Version, repository: Repository)

  // hashes should probably be handled as some number internally
  case class VersionHash(hash: String)

  // locally cached repo for the version
  case class VersionCachedRepo(version: VersionHash, file: File)

  def downloadGitRepo(repo: URI, cache: File): File = {

    val localRepo = util.localFile(repo.toString, cache)

    val clone = Git
      .cloneRepository()
      .setURI(repo.toString)
      .setDirectory(localRepo)
      .setBare(true)

    clone.call()
    localRepo
  }

  // needed for installSource
  // maybe implement some archiver to go directly to files?
  org.eclipse.jgit.archive.ArchiveFormats.registerAll()

  def installSource(revision: String, cachedRepo: File, target: File): Set[File] =
    IO.withTemporaryFile(target.getName,".zip") { tmp =>
      val out = new BufferedOutputStream(new FileOutputStream(tmp))

      Git.open(cachedRepo)
        .archive()
        .setFormat("zip")
        .setTree(ObjectId.fromString(revision))
        .setOutputStream(out)
        .call()

      IO.unzip(tmp, target)
    }

  def resolve(deps: Seq[Dependency]): Seq[VersionHash] = {
    deps.map { dep =>
      VersionHash(dep.version.hash)
      // TODO transitive resolve
    }
  }

}

object tasks {


}

object util {
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
  def localFile(url: String, cache: File): File = {
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