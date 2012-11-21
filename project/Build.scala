import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "medici-play"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "se.radley" %% "play-plugins-salat" % "1.1",
      "securesocial" % "securesocial_2.9.1" % "2.0.6"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      routesImport += "se.radley.plugin.salat.Binders._",
      templatesImport += "org.bson.types.ObjectId",
      resolvers += Resolver.url("SecureSocial Repository", url("http://securesocial.ws/repository/releases/"))(Resolver.ivyStylePatterns)
    )

}
