libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "1.3.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")

// causes some kind of dependency conflict
//addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.4.0")