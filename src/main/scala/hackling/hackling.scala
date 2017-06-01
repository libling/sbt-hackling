package hackling

import sbt.{File, URI}
import scala.collection.immutable.Seq

object Hackling {
  val defaultPaths = Seq("README.md", "doc", "src", "libling")
}

// a version is just a hash string for now
case class Version(hash: String)
// a repository is one or more git repository urls
case class Repositories private(gitRepos: Seq[URI])
object Repositories {
  def fromURIs(gitRepos: Seq[URI]): Repositories =
    Repositories(gitRepos.map(_.normalize).toVector)
}
case class Dependency(version: Version, repositories: Repositories)

// hashes should probably be handled as some number internally
case class VersionHash(hash: String)

/** Locally cached repo for the version.
  * the origin is useful to keep around as a place to look up a hash
  */
case class VersionCached(version: VersionHash, file: File, origin: Repositories)

