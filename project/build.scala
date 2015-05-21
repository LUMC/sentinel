import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._

object SentinelBuild extends Build {

  val Organization = "nl.lumc.sasc"
  val Name = "Sentinel"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.6"
  val JavaVersion = "1.8"
  val ScalatraVersion = "2.3.1"
  val Json4sVersion = "3.2.11"

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

  lazy val project = Project (
    "sentinel",
    file("."),
    settings = ScalatraPlugin.scalatraWithJRebel ++ scalariformSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      initialize := {
        val _ = initialize.value
        if (sys.props("java.specification.version") != JavaVersion)
          sys.error("Sentinel requires Java 8")
      },
      scalaVersion := ScalaVersion,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      resolvers += Classpaths.typesafeReleases,
      resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      resolvers += "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
      resolvers += "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
      ScalariformKeys.preferences := formattingPreferences,
      libraryDependencies ++= Seq(
        "ch.qos.logback"          %  "logback-classic"          % "1.1.2"               % "runtime",
        "com.github.fge"          %  "json-schema-validator"    % "2.2.6",
        "com.novus"               %% "salat"                    % "1.9.9",
        "com.typesafe.akka"       %% "akka-actor"               % "2.3.6",
        "commons-codec"           %  "commons-codec"            % "1.7",
        "commons-io"              %  "commons-io"               % "2.4",
        "de.flapdoodle.embed"     %  "de.flapdoodle.embed.mongo"% "1.47.3"              % "test",
        "javax.servlet"           %  "javax.servlet-api"        % "3.1.0"               % "container;provided;test",
        "net.databinder.dispatch" %% "dispatch-core"            % "0.11.2",
        "net.databinder.dispatch" %% "dispatch-json4s-jackson"  % "0.11.2",
        "org.eclipse.jetty"       %  "jetty-plus"               % "9.1.5.v20140505"     % "container",
        "org.eclipse.jetty"       %  "jetty-webapp"             % "9.1.5.v20140505"     % "container",
        "org.json4s"              %% "json4s-jackson"           % Json4sVersion,
        "org.json4s"              %% "json4s-mongo"             % Json4sVersion,
        "org.json4s"              %% "json4s-ext"               % Json4sVersion,
        "org.mongodb"             %% "casbah"                   % "2.8.0",
        "org.mindrot"             %  "jbcrypt"                  % "0.3m",
        "org.scalatra"            %% "scalatra"                 % ScalatraVersion,
        "org.scalatra"            %% "scalatra-specs2"          % ScalatraVersion       % "test",
        "org.scalatra"            %% "scalatra-json"            % ScalatraVersion,
        "org.scalatra"            %% "scalatra-swagger"         % ScalatraVersion,
        "org.scalatra"            %% "scalatra-swagger-ext"     % ScalatraVersion,
        "org.scalatra"            %% "scalatra-slf4j"           % ScalatraVersion
      )
    )
  )
}
