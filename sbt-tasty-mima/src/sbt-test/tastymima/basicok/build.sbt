import tastymima.intf._

scalaVersion := "3.7.1"
name := "test-project"

tastyMiMaPreviousArtifacts := Set(organization.value %% name.value % "0.0.1-SNAPSHOT")

tastyMiMaConfig ~= {
  _.withMoreProblemFilters(java.util.Arrays.asList(
    ProblemMatcher.make(ProblemKind.MissingClass, "lib.ClassOnlyInV1"),
  ))
}
