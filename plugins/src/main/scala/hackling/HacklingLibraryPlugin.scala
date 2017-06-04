package hackling

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.revwalk.RevCommit
import sbt.Keys.{sourceDirectory, _}
import sbt.{Def, _}

object HacklingLibraryPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = HacklingPlugin

  object autoImport {
    val liblingInit = taskKey[Option[RevCommit]]("initialize libling git repo")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    sourceDirectory in Compile := baseDirectory.value / "src",
    sourceDirectory in Test := baseDirectory.value / "test",
    unmanagedSourceDirectories in Compile := Seq((sourceDirectory in Compile).value),
    unmanagedSourceDirectories in Test := Seq((sourceDirectory in Test).value),

    liblingInit := {
      try {
        Git.open(baseDirectory.value)
        streams.value.log("this libling is already in a git repository")
        None
      } catch {
        case _: RepositoryNotFoundException =>
          val git = Git.init().setDirectory(baseDirectory.value).setBare(false).call()
          git.add().addFilepattern(".").setUpdate(false).call()
          val initial = git.commit().setMessage("a new libling is born").call()
          streams.value.log.info("A new libling is born. Git repository initialized.")
          Option(initial)
      }
    }
  )
}
