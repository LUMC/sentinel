import sbt._
import Keys._
import org.scalatra.sbt._
import com.earldouglas.xwp.XwpPlugin._
import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport._
import com.typesafe.sbt.SbtGit.GitCommand
import com.typesafe.sbt.SbtScalariform._
import com.typesafe.sbt.SbtSite.site
import de.heikoseeberger.sbtheader._
import net.virtualvoid.sbt.graph.Plugin.graphSettings
import sbtassembly.{ AssemblyKeys, MergeStrategy, PathList }, AssemblyKeys._
import uk.gov.hmrc.gitstamp.GitStampPlugin._

object SentinelBuild extends Build {

  val Release = "SNAPSHOT"
  val Version = "0.2" + { if (Release.endsWith("SNAPSHOT")) s"-$Release" else s".$Release" }

  val Organization = "nl.lumc.sasc"
  val ScalaSeries = "2.11"
  val ScalaVersion = s"$ScalaSeries.6"
  val JavaVersion = "1.8"
  val ScalatraVersion = "2.4.0"
  val Json4sVersion = "3.3.0"
  val JettyVersion = "9.2.14.v20151106"

  val scalaSeries = SettingKey[String]("scala-series", "The series of Scala used for building.")

  lazy val dependencies = Seq(
    "ch.qos.logback"          %  "logback-classic"            % "1.1.2"               % "runtime",
    "com.github.fakemongo"    %  "fongo"                      % "2.0.5"               % "it;test",
    "com.github.fge"          %  "json-schema-validator"      % "2.2.6",
    "com.novus"               %% "salat"                      % "1.9.9",
    "com.typesafe"            %  "config"                     % "1.3.0",
    "com.typesafe.akka"       %% "akka-actor"                 % "2.3.6",
    "commons-codec"           %  "commons-codec"              % "1.7",
    "commons-io"              %  "commons-io"                 % "2.4",
    "de.flapdoodle.embed"     %  "de.flapdoodle.embed.mongo"  % "1.50.2",
    "javax.servlet"           %  "javax.servlet-api"          % "3.1.0"               % "container;compile;provided;test;it",
    "org.eclipse.jetty"       %  "jetty-plus"                 % JettyVersion          % "container",
    "org.eclipse.jetty"       %  "jetty-webapp"               % JettyVersion          % "container;compile",
    "org.json4s"              %% "json4s-jackson"             % Json4sVersion,
    "org.json4s"              %% "json4s-mongo"               % Json4sVersion,
    "org.json4s"              %% "json4s-ext"                 % Json4sVersion,
    "org.mongodb"             %% "casbah"                     % "3.1.0",
    "de.svenkubiak"           %  "jBCrypt"                    % "0.4.1",
    "org.scalatra"            %% "scalatra"                   % ScalatraVersion,
    "org.scalatra"            %% "scalatra-specs2"            % ScalatraVersion,
    "org.scalatra"            %% "scalatra-json"              % ScalatraVersion,
    "org.scalatra"            %% "scalatra-swagger"           % ScalatraVersion,
    "org.scalatra"            %% "scalatra-swagger-ext"       % ScalatraVersion,
    "org.scalatra"            %% "scalatra-slf4j"             % ScalatraVersion,
    "org.specs2"              %% "specs2-junit"               % "3.6.6")

  lazy val formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(CompactStringConcatenation, false)
      .setPreference(CompactControlReadability, false)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(IndentLocalDefs, false)
      .setPreference(IndentPackageBlocks, true)
      .setPreference(IndentSpaces, 2)
      .setPreference(IndentWithTabs, false)
      .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
      .setPreference(PreserveDanglingCloseParenthesis, true)
      .setPreference(PreserveSpaceBeforeArguments, false)
      .setPreference(RewriteArrowSymbols, false)
      .setPreference(SpaceBeforeColon, false)
      .setPreference(SpaceInsideBrackets, false)
      .setPreference(SpaceInsideParentheses, false)
      .setPreference(SpacesWithinPatternBinders, true)
  }

  lazy val headerSettings = Seq(HeaderPlugin.autoImport.headers := Map(
    "scala" -> (
      HeaderPattern.cStyleBlockComment,
      """|/*
        | * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
        | *                         (see AUTHORS.md file for details).
        | *
        | * Licensed under the Apache License, Version 2.0 (the "License");
        | * you may not use this file except in compliance with the License.
        | * You may obtain a copy of the License at
        | *
        | * http://www.apache.org/licenses/LICENSE-2.0
        | *
        | * Unless required by applicable law or agreed to in writing, software
        | * distributed under the License is distributed on an "AS IS" BASIS,
        | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        | * See the License for the specific language governing permissions and
        | * limitations under the License.
        | */
        |""".stripMargin
      )))

  lazy val rootSettings = scalariformSettings ++
    Seq(
    initialize := {
      val _ = initialize.value
      val activeJavaVersion = sys.props("java.specification.version")
      if (activeJavaVersion != JavaVersion)
        sys.error(s"Sentinel requires Java version '$JavaVersion' -- found version '$activeJavaVersion'.")
    },
    scalaSeries := ScalaSeries,
    scalaVersion := ScalaVersion,
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-target:jvm-1.8",
      "-Xmax-classfile-name", "200"),
    cancelable in Global := true,
    shellPrompt := { state => s"$Version | ${GitCommand.prompt(state)}" },
    scapegoatConsoleOutput := false,
    // Since we use a lot of MongoDB operators, which look like interpolated strings.
    scapegoatDisabledInspections := Seq("LooksLikeInterpolatedString"),
    testOptions in Test += Tests.Argument("console", "junitxml"),
    testOptions in IntegrationTest += Tests.Argument("console", "junitxml"),
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    resolvers += Classpaths.typesafeReleases,
    resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    resolvers += "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    resolvers += "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
    ScalariformKeys.preferences := formattingPreferences)

  val commonSettings = rootSettings ++ headerSettings ++ ScalatraPlugin.scalatraSettings ++ graphSettings

  lazy val docsSiteSettings = site.settings ++ site.sphinxSupport() ++ site.includeScaladoc(s"scaladoc/$Version")

  lazy val sentinelCore = Project(
      id = "sentinel",
      base = file("sentinel"),
      settings = commonSettings ++ docsSiteSettings ++ Defaults.itSettings ++
        AutomateHeaderPlugin.automateFor(IntegrationTest) ++ Seq(
          organization := Organization,
          name := "sentinel",
          version := Version,
          unmanagedSourceDirectories in IntegrationTest <++= baseDirectory { base =>
            Seq(base / "src/it/scala", base / "src/test/scala/nl/lumc/sasc/sentinel/exts")
          },
          unmanagedResourceDirectories in IntegrationTest ++= (unmanagedResourceDirectories in Test).value,
          libraryDependencies ++= dependencies))
    .enablePlugins(AutomateHeaderPlugin)
    .configs(IntegrationTest)

  lazy val noPublish = Seq(publish := {}, publishLocal := {})

  lazy val JettyRunnerModule = "org.eclipse.jetty" % "jetty-runner" % JettyVersion % "container"

  lazy val sentinelLumc = Project(
      id = "sentinel-lumc",
      base = file("sentinel-lumc"),
      settings = noPublish ++ addCommandAlias("assembly-fulltest", ";test; it:test; assembly") ++
        jetty(Seq(JettyRunnerModule)) ++ Defaults.itSettings ++ gitStampSettings ++
        commonSettings ++ Seq(
          organization := Organization,
          name := "sentinel-lumc",
          version := Version,
          unmanagedResourceDirectories in IntegrationTest ++= (unmanagedResourceDirectories in Test).value,
          resourceGenerators in Compile <+= (resourceManaged, baseDirectory) map {
            (managedBase, base) =>
              val webappBase = base / "src" / "main" / "webapp"
              for {
                (from, to) <- webappBase ** "*" pair rebase(webappBase, managedBase / "main" / "webapp")
              } yield {
                Sync.copy(from, to)
                to
              }
          },
          mainClass in assembly := Some("nl.lumc.sasc.sentinel.JettyLauncher"),
          test in assembly := {},
          assemblyMergeStrategy in assembly := {
            // TODO: track down conflicting dependency for this library ~ for now it seems safe to take the first one
            case PathList("org", "apache", "commons", "collections", xs @ _*) => MergeStrategy.first
            // NOTE: these all come from the test dependencies, so we don't expect anything to break in runtime
            case PathList("org", "mockito", xs @ _*) => MergeStrategy.first
            case PathList("org", "objenesis", xs @ _*) => MergeStrategy.first
            case PathList("org", "hamcrest", xs @ _*) => MergeStrategy.first
            case PathList("scalac-plugin.xml") => MergeStrategy.discard
            case otherwise => (assemblyMergeStrategy in assembly).value(otherwise)
          },
          assemblyJarName in assembly := "Sentinel-" + Version + ".jar",
          libraryDependencies ++= Seq("ch.qos.logback" % "logback-classic" % "1.1.2" % "runtime"),
          dependencyOverrides ++= Set(JettyRunnerModule)))
    .enablePlugins(AutomateHeaderPlugin)
    .configs(IntegrationTest)
    .dependsOn(sentinelCore % "test->test; compile->compile")

  lazy val sentinelRoot = Project(
    id = "sentinel-root",
    base = file("."),
    settings = rootSettings ++ noPublish,
    aggregate = Seq(sentinelCore, sentinelLumc))
}
