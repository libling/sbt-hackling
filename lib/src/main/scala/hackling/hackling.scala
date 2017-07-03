package hackling

import java.io.File
import java.net.URI

import scala.collection.immutable.Seq

object Hackling {
  val defaultPaths = Seq("README.md", "doc", "src", "libling")
}

/** A commit hash, tag or branch. */
sealed trait Version

case class HashVersion(hash: String) extends Version

/** A tag or branch. */
case class NameVersion(name: String) extends Version


/** A repository is one or more git repository uris.
  * The assumption is that branches of the same origin may be contained in different repos
  * and we might want to check all of them to transparently resolve versions.
  */
case class Repositories private(gitRepos: Seq[URI])

object Repositories {
  def apply(gitRepos: URI*) = fromURIs(gitRepos.toVector)
  def fromURIs(gitRepos: Seq[URI]): Repositories =
    Repositories(gitRepos.map(_.normalize).toVector)
}

case class Dependency(version: Version, repositories: Repositories)


/** Locally cached repo for the version.
  * the origin is useful to keep around as a place to look up a hash
  */
case class VersionCached(version: HashVersion, file: File, origin: Repositories)
