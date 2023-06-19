
// Must stay in sync with TastyMiMaPlugin.TastyMiMaVersion
val TastyMiMaVersion = "0.3.1"

inThisBuild(Def.settings(
  crossScalaVersions := Seq("2.12.17"),
  scalaVersion := crossScalaVersions.value.head,

  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-encoding",
    "utf-8",
  ),

  scmInfo := Some(
    ScmInfo(
      url("https://github.com/scalacenter/sbt-tasty-mima"),
      "scm:git@github.com:scalacenter/sbt-tasty-mima.git",
      Some("scm:git:git@github.com:scalacenter/sbt-tasty-mima.git")
    )
  ),
  organization := "ch.epfl.scala",
  homepage := Some(url(s"https://github.com/scalacenter/sbt-tasty-mima")),
  licenses += (("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))),
  developers := List(
    Developer("sjrd", "Sébastien Doeraene", "sjrdoeraene@gmail.com", url("https://github.com/sjrd/")),
    Developer("bishabosha", "Jamie Thompson", "bishbashboshjt@gmail.com", url("https://github.com/bishabosha")),
  ),

  versionPolicyIntention := Compatibility.BinaryCompatible,
  // Ignore dependencies to internal modules whose version is like `1.2.3+4...` (see https://github.com/scalacenter/sbt-version-policy#how-to-integrate-with-sbt-dynver)
  versionPolicyIgnoredInternalDependencyVersions := Some("^\\d+\\.\\d+\\.\\d+\\+\\d+".r),
))

val strictCompileSettings = Seq(
  scalacOptions ++= Seq(
    "-Xfatal-warnings",
  ),
)

lazy val root = project.in(file("."))
  .aggregate(`sbt-tasty-mima`).settings(
    publish / skip := true,
  )

lazy val `sbt-tasty-mima` = project.in(file("sbt-tasty-mima"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-tasty-mima",

    strictCompileSettings,
    libraryDependencies += "ch.epfl.scala" % "tasty-mima-interface" % TastyMiMaVersion,

    // Skip `versionCheck` for snapshot releases
    versionCheck / skip := isSnapshot.value,

    scriptedBufferLog := false,
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
  )
