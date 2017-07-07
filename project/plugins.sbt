libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.0.0")
addSbtPlugin("com.eed3si9n" % "sbt-doge" % "0.1.5")

// causes some kind of dependency conflict
//addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.4.0")