package libling

import hackling.Hackling._
import org.scalatest.FlatSpec
import hackling._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import sbt._

class HacklingSpec extends FlatSpec {

  val dependencyHash1 = "5c151e3b43f6848541b4fceef775ccebf12d42fe"
  val dependencyHash2 = "a8128545d8cf2169bf95c08d4c0de0bac4f4136f"
  // TODO include in project test/resources
  val localRepoDir = file("/Users/jast/workspace/libling-skeleton/").getCanonicalFile
  def localDep(hash: String) =  Dependency(Version(hash), Repository(localRepoDir.toURI))
  val localDep1 = localDep(dependencyHash1)
  val localDep2 = localDep(dependencyHash2)

  "resolve" should "resolve a local git repo" in {
    IO.withTemporaryDirectory { cache =>
      val cached = util.resolve(cache)(Seq(localDep1))

      assert(cached.exists {
        case VersionCached(VersionHash(hash), repoDir) => hash == dependencyHash1 && repoDir == localRepoDir
      })
    }
  }

  "installSource" should "install sources into a directory" in {
    IO.withTemporaryDirectory { target =>
      val installed = installDepTo(target)

      assert(installed.nonEmpty)
      assert(util.installedLibs(target).contains(dependencyHash1))
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

  "installSources" should "install sources" in {
    IO.withTemporaryDirectory { temp =>
      val cache = temp / "cache"
      val target = temp / "target"
      val cachedDeps = util.resolve(cache)(Seq(localDep1))
      util.installSources(cache, target, defaultPaths, cachedDeps)
      assert(cachedDeps.map(_.version.hash) contains dependencyHash1)
    }
  }

  it should "remove installed sources when version is not in dependencies anymore" in {
    IO.withTemporaryDirectory { temp =>
      val cache = temp / "cache"
      val target = temp / "target"
      val cachedDeps = util.resolve(cache)(Seq(localDep1))
      util.installSources(cache, target, defaultPaths, cachedDeps)
      assert((target * dependencyHash1).get.nonEmpty)

      util.installSources(cache, target, defaultPaths, Seq.empty)
      assert((target * AllPassFilter).get.isEmpty)
    }
  }

  it should "remove installed sources and install new dependency when version changes" in {
    IO.withTemporaryDirectory { temp =>
      val cache = temp / "cache"
      val target = temp / "target"
      val cachedDeps1 = util.resolve(cache)(Seq(localDep1))
      util.installSources(cache, target, defaultPaths, cachedDeps1)
      assert((target * dependencyHash1).get.nonEmpty)

      val cachedDeps2 = util.resolve(cache)(Seq(localDep2))
      util.installSources(cache, target, defaultPaths, cachedDeps2)
      assert((target * dependencyHash1).get.isEmpty)
      assert((target * dependencyHash2).get.nonEmpty)
    }
  }

  def installDepTo(target: File) = {
    val revision = ObjectId.fromString(dependencyHash1)
    val repo = Git.open(localRepoDir)
    val libTarget = target / dependencyHash1
    util.installSource(revision, repo, defaultPaths, libTarget)
  }

  "canResolve" should "resolve hashes when not yet in local cache" in {
//    fail("not implemented")
  }

}