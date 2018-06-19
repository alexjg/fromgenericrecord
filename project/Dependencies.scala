import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0"
  lazy val avro = "org.apache.avro" % "avro" % "1.8.2"
  lazy val shapeless = "com.chuusai" %% "shapeless" % "2.3.3"
}
