package sbttastymima

import java.nio.file.{FileSystems, Path, Paths}

import sbt.{CrossVersion, _}
import sbt.Keys._
import sbt.librarymanagement._
import sbt.plugins.JvmPlugin

object TastyMiMaPlugin extends AutoPlugin {
  // Must stay in sync with TastyMiMaVersion in build.sbt
  private val TastyMiMaVersion = "0.2.0"

  object autoImport {
    val tastyMiMaPreviousArtifacts: SettingKey[Set[ModuleID]] =
      settingKey("Previous released artifacts used to test TASTy compatibility.")

    val tastyMiMaClasspath: TaskKey[Classpath] =
      taskKey("Classpath of tasty-mima itself")

    val tastyMiMaConfig: TaskKey[tastymima.intf.Config] =
      taskKey("Configuration for TASTy compatibility checks.")

    val tastyMiMaJavaBootClasspath: TaskKey[Seq[Path]] =
      taskKey("The bootclasspath of the JVM used to load artifacts for TASTy compatibility")

    val tastyMiMaPreviousClasspaths: TaskKey[Seq[(ModuleID, Seq[Path], Path)]] =
      taskKey("Classpaths and artifacts of previous releases used to test TASTy compatibility")

    val tastyMiMaCurrentClasspath: TaskKey[(Seq[Path], Path)] =
      taskKey("Classpath of the current artifact used to test TASTy compatibility")

    val tastyMiMaReportIssues: TaskKey[Unit] =
      taskKey("Check TASTy compatibility.")
  }

  import autoImport._

  override def requires: Plugins = JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  override def globalSettings: Seq[Setting[_]] = Def.settings(
    tastyMiMaConfig := new tastymima.intf.Config(),
    tastyMiMaJavaBootClasspath := {
      System.getProperty("sun.boot.class.path") match {
        case null =>
          Seq(FileSystems.getFileSystem(java.net.URI.create("jrt:/")).getPath("modules", "java.base"))

        case bootClasspath =>
          val rtJarFile = bootClasspath
            .split(java.io.File.pathSeparatorChar)
            .find { path =>
              new java.io.File(path).getName() == "rt.jar"
            }
            .getOrElse {
              throw new MessageOnlyException(s"cannot find rt.jar in $bootClasspath")
            }
          Seq(Paths.get(rtJarFile))
      }
    },
  )

  override def projectSettings: Seq[Setting[_]] = Def.settings(
    tastyMiMaPreviousArtifacts := Set.empty,
    tastyMiMaClasspath / dependencyResolution := {
      val config = csrConfiguration.value.withAutoScalaLibrary(false)
      lmcoursier.CoursierDependencyResolution(config)
    },
    tastyMiMaClasspath := {
      val s = streams.value
      val log = s.log
      val lm = (tastyMiMaClasspath / dependencyResolution).value
      val retrieveDir = s.cacheDirectory / "retrieve"

      val moduleID = "ch.epfl.scala" % "tasty-mima_3" % TastyMiMaVersion
      lm.retrieve(moduleID, None, retrieveDir, log) match {
        case Left(unresolvedWarning) =>
          throw unresolvedWarning.resolveException
        case Right(cp) =>
          Attributed.blankSeq(cp)
      }
    },
    tastyMiMaPreviousClasspaths / dependencyResolution := dependencyResolution.value,
    tastyMiMaPreviousClasspaths := {
      val s = streams.value
      val log = s.log
      val lm = (tastyMiMaPreviousClasspaths / dependencyResolution).value
      val retrieveDir = s.cacheDirectory / "retrieve"
      val scalaModInfo = scalaModuleInfo.value

      val javaBootCp = tastyMiMaJavaBootClasspath.value
      val artifactIDs = tastyMiMaPreviousArtifacts.value

      // Build updateConfiguration like `lm.retrieve()` would do
      val retrieveConfiguration = RetrieveConfiguration()
        .withRetrieveDirectory(retrieveDir)
      val updateConfiguration = UpdateConfiguration()
        .withRetrieveManaged(retrieveConfiguration)

      for (artifactID0 <- artifactIDs.toList) yield {
        val artifactID = scalaModInfo match {
          case None =>
            artifactID0
          case Some(modInfo) =>
            CrossVersion(modInfo.scalaFullVersion, modInfo.scalaBinaryVersion)(artifactID0)
              .withCrossVersion(CrossVersion.disabled)
        }
        val module = lm.wrapDependencyInModule(artifactID, scalaModInfo)

        lm.update(module, updateConfiguration, UnresolvedWarningConfiguration(), log) match {
          case Left(unresolvedWarning) =>
            throw unresolvedWarning.resolveException

          case Right(updateReport) =>
            val cp0: Seq[(ModuleID, Path)] = for {
              config <- updateReport.configurations
              m <- config.modules
              (artifact, file) <- m.artifacts
            } yield {
              m.module -> file.toPath()
            }
            val cp: Seq[Path] = javaBootCp ++ cp0.map(_._2)

            val entry: Path = cp0.find { pair =>
              val module = pair._1
              module.organization == artifactID.organization && module.name == artifactID.name
            }.getOrElse {
              throw new MessageOnlyException(s"Could not find entry for $artifactID in " + cp0.mkString("\n", "\n", ""))
            }._2

            (artifactID, cp, entry)
        }
      }
    },
    tastyMiMaCurrentClasspath := {
      val javaBootCp = tastyMiMaJavaBootClasspath.value
      val classDir = (Compile / classDirectory).value.toPath()
      val jar = (Compile / packageBin / artifactPath).value.toPath()
      val cp0 = Attributed.data((Compile / fullClasspath).value).map(_.toPath())

      val cp: Seq[Path] = javaBootCp ++ cp0
      val entry: Path = cp0.find { path =>
        path == classDir || path == jar
      }.getOrElse {
        throw new MessageOnlyException(s"Could not find entry $classDir or $jar in " + cp0.mkString("\n", "\n", ""))
      }

      (cp, entry)
    },
    tastyMiMaReportIssues := {
      val projectID = moduleName.value
      val log = streams.value.log

      val tastyMiMaCp = Attributed.data(tastyMiMaClasspath.value).map(_.toURI().toURL()).toArray
      val config = tastyMiMaConfig.value
      val tastyMiMa = tastymima.intf.TastyMiMa.newInstance(tastyMiMaCp, getClass().getClassLoader(), config)

      val previousCps = tastyMiMaPreviousClasspaths.value
      val currentCp = tastyMiMaCurrentClasspath.value

      analyzeAndReport(projectID, log, tastyMiMa, previousCps, currentCp)
    },
  )

  private def analyzeAndReport(
    projectID: String,
    log: Logger,
    tastyMiMa: tastymima.intf.TastyMiMa,
    previousCps: Seq[(ModuleID, Seq[Path], Path)],
    currentCp: (Seq[Path], Path),
  ): Unit = {
    import scala.collection.JavaConverters._

    val newClasspath = currentCp._1.asJava
    val newClasspathEntry = currentCp._2

    var anyFailed: Boolean = false

    for (previousCp <- previousCps) {
      val previousID = previousCp._1
      val oldClasspath = previousCp._2.asJava
      val oldClasspathEntry = previousCp._3

      val problems = tastyMiMa.analyze(oldClasspath, oldClasspathEntry, newClasspath, newClasspathEntry).asScala

      if (problems.nonEmpty) {
        log.error(s"TASTy compatibility check failed for $projectID with respect to $previousID")
        log.error(s"The following incompatibilities were found:")
        for (problem <- problems) {
          log.error("* " + problem.getDescription())
          log.error("  filter with: " + problem.getFilterIncantation())
        }
        anyFailed = true
      }
    }

    if (anyFailed)
      throw new MessageOnlyException(s"TASTy compatibility check failed for $projectID")
    else
      log.info(s"TASTy compatibility check succeeded for $projectID")
  }
}
