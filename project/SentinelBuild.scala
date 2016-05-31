import sbt._
import Keys._
import org.scalatra.sbt._
import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport._
import com.typesafe.sbt.SbtGit.GitCommand
import com.typesafe.sbt.SbtScalariform._
import com.typesafe.sbt.SbtSite.site
import de.heikoseeberger.sbtheader._
import net.virtualvoid.sbt.graph.Plugin.graphSettings
import java.util.Calendar

object SentinelBuild extends Build {

  val Release = "SNAPSHOT"
  val Version = "0.2" + { if (Release.endsWith("SNAPSHOT")) s"-$Release" else s".$Release" }
  val Homepage = "https://github.com/LUMC/sentinel"
  object License {
    val Name = "Apache License, Version 2.0"
    val Url = "http://www.apache.org/licenses/LICENSE-2.0"
  }

  val Organization = "nl.lumc.sasc"
  val ScalaSeries = "2.11"
  val ScalaVersion = s"$ScalaSeries.6"
  val JavaVersion = "1.8"
  val ScalatraVersion = "2.4.0"
  val Json4sVersion = "3.3.0"

  val scalaSeries = SettingKey[String]("scala-series", "The series of Scala used for building.")

  lazy val dependencies = Seq(
    "ch.qos.logback"          %  "logback-classic"            % "1.1.2"               % "runtime",
    "com.github.fakemongo"    %  "fongo"                      % "2.0.5"               % "it;test",
    "com.github.fge"          %  "json-schema-validator"      % "2.2.6",
    "com.novus"               %% "salat"                      % "1.9.9",
    "com.typesafe"            %  "config"                     % "1.3.0",
    "commons-codec"           %  "commons-codec"              % "1.7",
    "commons-io"              %  "commons-io"                 % "2.4",
    "de.flapdoodle.embed"     %  "de.flapdoodle.embed.mongo"  % "1.50.2",
    "de.svenkubiak"           %  "jBCrypt"                    % "0.4.1",
    "org.json4s"              %% "json4s-jackson"             % Json4sVersion,
    "org.json4s"              %% "json4s-mongo"               % Json4sVersion,
    "org.json4s"              %% "json4s-ext"                 % Json4sVersion,
    "org.mongodb"             %% "casbah"                     % "3.1.0",
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
      .setPreference(DanglingCloseParenthesis, Preserve)
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
      s"""|/*
        | * Copyright (c) 2015-${Calendar.getInstance.get(Calendar.YEAR)} Leiden University Medical Center and contributors
        | *                         (see AUTHORS.md file for details).
        | *
        | * Licensed under the ${License.Name} (the "License");
        | * you may not use this file except in compliance with the License.
        | * You may obtain a copy of the License at
        | *
        | * ${License.Url}
        | *
        | * Unless required by applicable law or agreed to in writing, software
        | * distributed under the License is distributed on an "AS IS" BASIS,
        | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        | * See the License for the specific language governing permissions and
        | * limitations under the License.
        | */
        |""".stripMargin
      )))

  lazy val publishSettings = Seq(
    licenses := Seq(License.Name -> url(License.Url)),
    homepage := Some(url(Homepage)),
    publishMavenStyle := true,
    publishTo := {
      if (isSnapshot.value) Option(Resolver.sonatypeRepo("snapshots"))
      else Option(Resolver.sonatypeRepo("releases"))
    },
    publishArtifact in Test := false,
    publishArtifact in IntegrationTest := false,
    pomExtra := {
      <scm>
        <url>git@github.com:LUMC/sentinel.git</url>
        <connection>scm:git:git@github.com:LUMC/sentinel.git</connection>
      </scm>
        <developers>
          <developer>
            <id>bow</id>
            <name>Wibowo Arindrarto</name>
            <email>w.arindrarto@lumc.nl</email>
          </developer>
        </developers>
    })

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
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    ScalariformKeys.preferences := formattingPreferences)

  val commonSettings = rootSettings ++ publishSettings ++ headerSettings ++ ScalatraPlugin.scalatraSettings ++ graphSettings

  lazy val docsSiteSettings = site.settings ++ site.sphinxSupport() ++ site.includeScaladoc(s"scaladoc/$Version")

  lazy val sentinelCore = Project(
      id = "sentinel-core",
      base = file("sentinel-core"),
      settings = commonSettings ++ docsSiteSettings ++ Defaults.itSettings ++
        AutomateHeaderPlugin.automateFor(Compile) ++ Seq(
          organization := Organization,
          name := "sentinel-core",
          version := Version,
          unmanagedSourceDirectories in IntegrationTest <++= baseDirectory { base =>
            Seq(base / "src/it/scala", base / "src/test/scala/nl/lumc/sasc/sentinel/exts")
          },
          unmanagedResourceDirectories in IntegrationTest ++= (unmanagedResourceDirectories in Test).value,
          libraryDependencies ++= dependencies))
    .enablePlugins(AutomateHeaderPlugin)
    .configs(IntegrationTest)

  lazy val sentinel = Project(
    id = "sentinel",
    base = file("."),
    settings = rootSettings ++ Seq(
      publishLocal := {},
      publish := {}
    ),
    aggregate = Seq(sentinelCore))
}
