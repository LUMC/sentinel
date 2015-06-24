import sbt._
import Keys._
import org.scalatra.sbt._
import com.earldouglas.xwp.XwpPlugin._
import com.typesafe.sbt.SbtScalariform._
import com.typesafe.sbt.SbtSite.site
import de.heikoseeberger.sbtheader._
import sbtassembly.{ AssemblyKeys, MergeStrategy, PathList }, AssemblyKeys._

object SentinelBuild extends Build {

  val Organization = "nl.lumc.sasc"
  val Name = "Sentinel"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.6"
  val JavaVersion = "1.8"
  val ScalatraVersion = "2.3.1"
  val Json4sVersion = "3.2.11"
  val JettyVersion = "9.2.10.v20150310"
  val JettyRunnerModule = "org.eclipse.jetty" % "jetty-runner" % JettyVersion % "container"

  lazy val dependencies = Seq(
    "ch.qos.logback"          %  "logback-classic"            % "1.1.2"               % "runtime",
    "com.github.fge"          %  "json-schema-validator"      % "2.2.6",
    "com.novus"               %% "salat"                      % "1.9.9",
    "com.typesafe.akka"       %% "akka-actor"                 % "2.3.6",
    "commons-codec"           %  "commons-codec"              % "1.7",
    "commons-io"              %  "commons-io"                 % "2.4",
    "de.flapdoodle.embed"     %  "de.flapdoodle.embed.mongo"  % "1.47.3"              % "test;it",
    "javax.servlet"           %  "javax.servlet-api"          % "3.1.0"               % "container;compile;provided;test;it",
    "net.databinder.dispatch" %% "dispatch-core"              % "0.11.2",
    "net.databinder.dispatch" %% "dispatch-json4s-jackson"    % "0.11.2",
    "org.eclipse.jetty"       %  "jetty-plus"                 % JettyVersion          % "container",
    "org.eclipse.jetty"       %  "jetty-webapp"               % JettyVersion          % "container;compile",
    "org.json4s"              %% "json4s-jackson"             % Json4sVersion,
    "org.json4s"              %% "json4s-mongo"               % Json4sVersion,
    "org.json4s"              %% "json4s-ext"                 % Json4sVersion,
    "org.mongodb"             %% "casbah"                     % "2.8.0",
    "org.mindrot"             %  "jbcrypt"                    % "0.3m",
    "org.scalatra"            %% "scalatra"                   % ScalatraVersion,
    "org.scalatra"            %% "scalatra-specs2"            % ScalatraVersion       % "test;it",
    "org.scalatra"            %% "scalatra-json"              % ScalatraVersion,
    "org.scalatra"            %% "scalatra-swagger"           % ScalatraVersion,
    "org.scalatra"            %% "scalatra-swagger-ext"       % ScalatraVersion,
    "org.scalatra"            %% "scalatra-slf4j"             % ScalatraVersion)

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
        | * Copyright (c) 2015 Leiden University Medical Center and contributors
        | *                    (see AUTHORS.md file for details).
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
      ))) ++ AutomateHeaderPlugin.automateFor(IntegrationTest)

  lazy val jettyRunnerSettings = jetty(Seq(JettyRunnerModule))

  lazy val projectSettings = ScalatraPlugin.scalatraWithJRebel ++ scalariformSettings ++ jettyRunnerSettings ++
    site.settings ++ site.sphinxSupport() ++ site.includeScaladoc() ++ headerSettings ++ Defaults.itSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      initialize := {
        val _ = initialize.value
        if (sys.props("java.specification.version") != JavaVersion)
          sys.error("Sentinel requires Java 8.")
      },
      scalaVersion := ScalaVersion,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      testOptions in Test += Tests.Argument("console", "junitxml"),
      testOptions in IntegrationTest += Tests.Argument("console", "junitxml"),
      mainClass in assembly := Some("nl.lumc.sasc.sentinel.JettyLauncher"),
      test in assembly := {
        (test in Test).value
        (test in IntegrationTest).value
      },
      assemblyMergeStrategy in assembly := {
        // TODO: track down conflicting dependency for this library ~ for now it seems safe to take the first one
        case PathList("org", "apache", "commons", "collections", xs @ _*) => MergeStrategy.first
        case otherwise => (assemblyMergeStrategy in assembly).value(otherwise)
      },
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      resolvers += Classpaths.typesafeReleases,
      resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      resolvers += "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
      resolvers += "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
      ScalariformKeys.preferences := formattingPreferences,
      dependencyOverrides ++= Set(JettyRunnerModule),
      libraryDependencies ++= dependencies)

  lazy val project = Project("sentinel",  file("."))
    .enablePlugins(AutomateHeaderPlugin)
    .configs(IntegrationTest)
    .settings(projectSettings)
}
