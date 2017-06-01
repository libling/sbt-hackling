package hackling

import sbt.{Def, _}
import sbt.Keys.{sourceDirectory, _}

object HacklingLibraryPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = HacklingPlugin

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    sourceDirectory in Compile := baseDirectory.value / "src",
    sourceDirectory in Test := baseDirectory.value / "test",
    unmanagedSourceDirectories in Compile := Seq((sourceDirectory in Compile).value),
    unmanagedSourceDirectories in Test := Seq((sourceDirectory in Test).value)
  )
}
