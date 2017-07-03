package hackling
package internal

import hackling.Hackling._
import hackling.internal.util._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import sbt.{uri, _}

import scala.collection.immutable.Seq

class HacklingSpec extends FlatSpec with BeforeAndAfterAll {

  val dependencyHash1 = "5c151e3b43f6848541b4fceef775ccebf12d42fe"
  val dependencyHash2 = "a8128545d8cf2169bf95c08d4c0de0bac4f4136f"
  // TODO include in project test/resources
  val localRepoDir = IO.createTemporaryDirectory.getCanonicalFile
  val remoteRepoURI = uri("https://github.com/libling/libling-skeleton.git")
  val remoteRepoWithDependenciesURI = uri("https://github.com/libling/libling-with-dependencies")
  val dependencyHashInRepoWithDeps = "eb322e1d49604cf4d49986e14d0a0672d7c22094"
  val dependencyHashTransitive = "ef33ab5a6eac7af6b2f6a8d238ccdc88e25171a2"

  def localDep(hash: String) = Dependency(HashVersion(hash), Repositories(localRepoDir.toURI))
  val localDep1 = localDep(dependencyHash1)
  val localDep2 = localDep(dependencyHash2)

  def remoteDep(hash: String, uri: URI) = Dependency(HashVersion(hash), Repositories(uri))
  def remoteDepNamed(name: String, uri: URI) = Dependency(NameVersion(name), Repositories(uri))

  val remoteDep1 = remoteDep(dependencyHash1, remoteRepoURI)

  override def beforeAll(): Unit = {
    Git.cloneRepository().setDirectory(localRepoDir).setURI(remoteRepoURI.toString).call()
    assert(localRepoDir.exists())
  }

  override def afterAll(): Unit = {
    IO.delete(localRepoDir)
  }

  "resolve" should "resolve a local git repo to its local address" in {
    IO.withTemporaryDirectory { cache =>
      val cached = taskImpl.resolve(cache)(Seq(localDep1))

      assert(cached.nonEmpty)
      assert(cached.exists {
        case VersionCached(HashVersion(hash), cacheRepoDir, _) => hash == dependencyHash1 && cacheRepoDir == localRepoDir
      })
    }
  }

  it should "resolve a remote git repo and cache it" in {
    IO.withTemporaryDirectory { cache =>
      val cached = taskImpl.resolve(cache)(Seq(remoteDep1))

      val cacheContents = (cache * DirectoryFilter).get
      assert(cacheContents.nonEmpty)
      assert {
        withRepo(cached.head.file) { git =>
          val commit = ObjectId.fromString(remoteDep1.version.asInstanceOf[HashVersion].hash)
          git.log().add(commit).call().iterator().hasNext
        }
      }
    }
  }

  it should "resolve transitive dependencies" in {
    val remoteDepWithDependencies = remoteDep(dependencyHashInRepoWithDeps, remoteRepoWithDependenciesURI)

    IO.withTemporaryDirectory { cache =>
      val cached = taskImpl.resolve(cache)(Seq(remoteDepWithDependencies))
      val hashes = cached.map(_.version.hash)
      assert(hashes.contains(dependencyHashInRepoWithDeps))
      assert(hashes.contains(dependencyHashTransitive))
    }
  }

  it should "resolve a hash by tag" in {
    val remoteDepTag = remoteDepNamed("v0.2.1", remoteRepoURI)
    val remoteDepTagHash = "ad1b54018eb97da360fe19f7ccb2b43ed42b700d"

    IO.withTemporaryDirectory { cache =>
      val cached = taskImpl.resolve(cache)(Seq(remoteDepTag))
      val hashes = cached.map(_.version.hash)
      assert(hashes.contains(remoteDepTagHash))
    }
  }

  it should "resolve a hash by branch name" in {
    val remoteDepBranch = remoteDepNamed("skeleton-in-cellar", remoteRepoURI)
    val remoteDepBranchHash = "ef33ab5a6eac7af6b2f6a8d238ccdc88e25171a2"

    IO.withTemporaryDirectory { cache =>
      val cached = taskImpl.resolve(cache)(Seq(remoteDepBranch))
      val hashes = cached.map(_.version.hash)
      assert(hashes.contains(remoteDepBranchHash))
    }
  }

  "installSource" should "install sources into a directory" in {
    IO.withTemporaryDirectory { target =>
      val installed = installDepTo(target)

      assert(installed.nonEmpty)
      assert(taskImpl.installedLibs(target).contains(dependencyHash1))
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
      val cachedDeps = taskImpl.resolve(cache)(Seq(localDep1))
      taskImpl.installSources(target, defaultPaths, cachedDeps)
      assert(cachedDeps.map(_.version.hash) contains dependencyHash1)
    }
  }

  it should "remove installed sources when version is not in dependencies anymore" in {
    IO.withTemporaryDirectory { temp =>
      val cache = temp / "cache"
      val target = temp / "target"
      val cachedDeps = taskImpl.resolve(cache)(Seq(localDep1))
      taskImpl.installSources(target, defaultPaths, cachedDeps)
      assert((target * dependencyHash1).get.nonEmpty)

      taskImpl.installSources(target, defaultPaths, Seq.empty)
      assert((target * AllPassFilter).get.isEmpty)
    }
  }

  it should "remove installed sources and install new dependency when version changes" in {
    IO.withTemporaryDirectory { temp =>
      val cache = temp / "cache"
      val target = temp / "target"
      val cachedDeps1 = taskImpl.resolve(cache)(Seq(localDep1))
      taskImpl.installSources(target, defaultPaths, cachedDeps1)
      assert((target * dependencyHash1).get.nonEmpty)

      val cachedDeps2 = taskImpl.resolve(cache)(Seq(localDep2))
      taskImpl.installSources(target, defaultPaths, cachedDeps2)
      assert((target * dependencyHash1).get.isEmpty)
      assert((target * dependencyHash2).get.nonEmpty)
    }
  }

  def installDepTo(target: File) = {
    val revision = ObjectId.fromString(dependencyHash1)
    val repo = Git.open(localRepoDir)
    val libTarget = target / dependencyHash1
    taskImpl.installSource(repo, defaultPaths, revision, libTarget)
  }

}
