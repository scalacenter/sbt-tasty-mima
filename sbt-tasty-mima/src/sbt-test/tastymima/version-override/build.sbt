import tastymima.intf._

scalaVersion := "3.4.0"
name := "test-project"

tastyMiMaVersionOverride := Some("1.1.0")
tastyMiMaTastyQueryVersionOverride := Some("1.3.0")

tastyMiMaPreviousArtifacts := Set(organization.value %% name.value % "0.0.1-SNAPSHOT")

tastyMiMaConfig ~= {
  _.withMoreProblemFilters(java.util.Arrays.asList(
    ProblemMatcher.make(ProblemKind.MissingClass, "lib.ClassOnlyInV1"),
  ))
}
