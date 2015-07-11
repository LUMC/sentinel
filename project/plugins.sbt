resolvers += Resolver.url("hmrc-sbt-plugin-releases",
                          url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0")

addSbtPlugin("com.mojolly.scalate" % "xsbt-scalate-generator" % "0.5.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.1")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.0")

addSbtPlugin("org.scalatra.sbt" % "scalatra-sbt" % "0.4.0")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.0.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.4")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-stamp" % "5.1.0")
