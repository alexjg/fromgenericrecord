import Dependencies._

lazy val repo = "fromgenericrecord"
lazy val username = "alexjg"

lazy val publishSettings = Seq(
  homepage := Some(url(s"https://github.com/$username/$repo")),
  licenses += "MIT" -> url(s"https://github.com/$username/$repo/blob/master/LICENSE"),
  scmInfo := Some(ScmInfo(url(s"https://github.com/$username/$repo"), s"git@github.com:$username/$repo.git")),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  developers := List(
    Developer(id = username,
              name = "Alex Good",
              email = "alex@memoryandthought.me",
              url = new URL(s"http://github.com/${username}"))
  ),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  publishTo := {
	val nexus = "https://oss.sonatype.org/"
	if (isSnapshot.value)
	  Some("snapshots" at nexus + "content/repositories/snapshots")
	else
	  Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
)

import ReleaseTransformations._
lazy val releaseSettings = Seq(
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    //runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommand("publishSigned"),
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  )
)

lazy val root = (project in file(".")).
  settings(publishSettings: _*).
  settings(releaseSettings: _*).
  settings(
    inThisBuild(List(
      organization := "me.memoryandthought",
      scalaVersion := "2.12.6",
      version      := "0.0.1-SNAPSHOT"
    )),
    name := "fromgenericrecord",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies += scalaTest % Test,
    libraryDependencies += scalaCheck % Test,
    libraryDependencies += avro,
    libraryDependencies += shapeless,
  )

