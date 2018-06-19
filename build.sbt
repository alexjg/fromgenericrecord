import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "org.memoryandthought",
      scalaVersion := "2.12.6",
      version      := "0.0.1"
    )),
    name := "fromgenericrecord",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies += scalaTest % Test,
    libraryDependencies += scalaCheck % Test,
    libraryDependencies += avro,
    libraryDependencies += shapeless
  )
