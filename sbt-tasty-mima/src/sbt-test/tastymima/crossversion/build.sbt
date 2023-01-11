import tastymima.intf._

crossScalaVersions := Seq("3.1.0", "2.13.10", "2.12.17")
scalaVersion := "3.1.0"
name := "test-project"

tastyMiMaPreviousArtifacts := Set(organization.value %% name.value % "0.0.1-SNAPSHOT")

tastyMiMaConfig ~= {
  _.withMoreProblemFilters(java.util.Arrays.asList(
    ProblemMatcher.make(ProblemKind.MissingClass, "lib.ClassOnlyInV1"),
  ))
}
