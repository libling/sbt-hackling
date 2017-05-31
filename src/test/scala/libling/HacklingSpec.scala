package libling

import hackling.Hackling._
import org.scalatest.FlatSpec
import hackling._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import sbt._

class HacklingSpec extends FlatSpec {

  val dependencyHash = "5c151e3b43f6848541b4fceef775ccebf12d42fe"
  // TODO include in project test/resources
  val localRepoDir = file("/Users/jast/workspace/libling-skeleton/").getCanonicalFile
  val localDep = Dependency(Version(dependencyHash), Repository(localRepoDir.toURI))

  "resolve" should "resolve a local git repo" in {
    IO.withTemporaryDirectory { cache =>
      val cached = util.resolve(cache)(Seq(localDep))

      assert(cached.exists {
        case VersionCached(VersionHash(hash), repoDir) => hash == dependencyHash && repoDir == localRepoDir
      })
    }
  }

  "installSource" should "install sources into a directory" in {
    IO.withTemporaryDirectory { target =>
      val installed = installDepTo(target)

      assert(installed.nonEmpty)
      assert(util.installedLibs(target).contains(dependencyHash))
      val allInTarget = target.***.get
      assert(installed.forall(f => allInTarget contains f))
      assert(installed.forall(_.isFile))
    }
  }

  it should "return installed sources when installing already installed dependency" in {
    IO.withTemporaryDirectory { target =>
      val installed1 = installDepTo(target)

      val allInTarget = target.***.get

      assert(installed1.forall(f => allInTarget contains f))

      val installed2 = installDepTo(target)
      assert(installed2.forall(f => allInTarget contains f))
      assert(installed2.forall(f => installed1 contains f))
    }
  }

  def installDepTo(target: File) = {
    val revision = ObjectId.fromString(dependencyHash)
    val repo = Git.open(localRepoDir)
    val libTarget = target / dependencyHash
    util.installSource(revision, repo, defaultPaths, libTarget)
  }

  "canResolve" should "resolve hashes when not yet in local cache" in {
//    fail("not implemented")
  }

}
