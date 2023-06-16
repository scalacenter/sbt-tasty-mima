import tastymima.intf._

scalaVersion := "3.3.0"
name := "test-project"

tastyMiMaVersionOverride := Some("0.3.0")
tastyMiMaTastyQueryVersionOverride := Some("0.8.1")

tastyMiMaPreviousArtifacts := Set(organization.value %% name.value % "0.0.1-SNAPSHOT")

tastyMiMaConfig ~= {
  _.withMoreProblemFilters(java.util.Arrays.asList(
    ProblemMatcher.make(ProblemKind.MissingClass, "lib.ClassOnlyInV1"),
  ))
}
