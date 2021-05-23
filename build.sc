import $file.webpack
import $file.reboot

import mill._
import scalalib._
import scalafmt._
import mill.scalajslib._
import webpack.{WebpackModule, NpmDependency}

val catsEffectDep = ivy"org.typelevel::cats-effect::2.3.1"

val monocleDeps = Agg(
  "monocle-core",
  "monocle-macro"
).map { dep => ivy"com.github.julien-truffaut::${dep}::2.1.0" }

val sjsVersion = "1.5.0"

val commonScalacOptions =
  Seq(
    "-language:implicitConversions",
    "-feature",
    "-deprecation",
    "-Xfatal-warnings",
    "-language:higherKinds",
    "-language:existentials",
    "-Wunused",
    "-encoding",
    "UTF-8",
    "-Yrangepos"
  )

trait ProtocolModule extends ScalaModule {

  def scalaVersion = "2.13.1"
  def scalacOptions = commonScalacOptions

  def ivyDeps =
    Agg(ivy"org.typelevel::cats-core::2.6.1") ++ Agg(
      "circe-core",
      "circe-parser",
      "circe-generic"
    ).map { dep => ivy"io.circe::${dep}::0.13.0" }

  def millSourcePath = build.millSourcePath / "protocol"

}

object protocolJs extends ProtocolModule with ScalaJSModule {
  def scalaJSVersion = sjsVersion
}

object protocolJvm extends ProtocolModule

object server extends ScalaModule {

  def scalaVersion = "2.13.1"
  def moduleDeps = Seq(protocolJvm)
  def compileIvyDeps = Agg(ivy"org.typelevel:::kind-projector::0.11.0")
  def scalacOptions = commonScalacOptions
  def scalacPluginIvyDeps =
    Agg(
      ivy"org.typelevel:::kind-projector::0.11.0",
      ivy"com.olegpy::better-monadic-for::0.3.1"
    )

  def ivyDeps =
    Agg(
      catsEffectDep,
      ivy"io.chrisdavenport::log4cats-slf4j::1.1.1",
      ivy"ch.qos.logback:logback-classic:1.2.3",
      ivy"com.spotify::magnolify-cats::0.4.1"
    ) ++ Agg(
      "http4s-dsl",
      "http4s-circe",
      "http4s-blaze-server",
      "http4s-blaze-client"
    ).map { dep => ivy"org.http4s::${dep}::0.21.13" } ++ Agg(
      "tapir-core",
      "tapir-json-circe",
      "tapir-http4s-server",
      "tapir-openapi-docs",
      "tapir-openapi-circe-yaml",
      "tapir-redoc-http4s"
    ).map { dep => ivy"com.softwaremill.sttp.tapir::${dep}::0.16.16" } ++ Agg(
      "fs2-io",
      "fs2-core"
    ).map { dep => ivy"co.fs2::${dep}::2.4.6" } ++ monocleDeps ++
      Agg(ivy"io.chrisdavenport::cats-time::0.3.4")

  def assetDir = T.source {
    millSourcePath / "src" / "main" / "resources"
  }

  def localSslKey = T.source {
    millSourcePath / "src" / "main" / "resources" / "localkey.pkcs12"
  }

  object test extends Tests {

    def ivyDeps =
      Agg(
        ivy"org.scalameta::munit::0.7.22",
        ivy"org.typelevel::discipline-munit::1.0.9",
        ivy"org.typelevel::cats-laws::2.6.1",
        ivy"org.scalacheck::scalacheck::1.15.4" 
      )
    def testFrameworks = Seq("munit.Framework")
  }
}

object client extends ScalaJSModule {
  def scalaVersion = "2.13.1"
  def scalaJSVersion = sjsVersion
  def scalacPluginIvyDeps =
    super.scalacPluginIvyDeps() ++ Agg(
      ivy"com.olegpy::better-monadic-for:0.3.1"
    )
  def scalacOptions = commonScalacOptions
  def moduleDeps = Seq(protocolJs)
  def ivyDeps =
    monocleDeps ++ Agg(
      ivy"io.github.cquiroz::scala-java-time::2.3.0",
      catsEffectDep
    ) ++ Agg(
      "core",
      "extra",
      "ext-cats"
    ).map { dep => ivy"com.github.japgolly.scalajs-react::${dep}::1.7.7" }
}

trait WebModule extends WebpackModule {

  def webpackVersion = "4.17.1"
  def webpackCliVersion = "3.1.0"
  def webpackDevServerVersion = "3.1.7"
  def sassVersion = "1.25.0"
  def npmDeps =
    Agg("react" -> "React", "react-dom" -> "ReactDOM").map { case (n, g) =>
      NpmDependency(n, "16.7.0", g)
    }

  def millSourcePath = build.millSourcePath / "web"

}

object web extends WebModule {

  import ammonite.ops._
  import ammonite.ops.ImplicitWd._

  def mainJS = client.fastOpt
  def development = true

  import mill.eval._
  import mill.define.Task
  import mill.api.Strict.{Agg => SAgg}

  def runBackground(ev: Evaluator) = T.command {
    val t0 = bundle
    val t1 = server.assetDir
    val t2 = server.localSslKey
    val r = ev.evaluate(SAgg[Task[_]](t0, t1, t2)).results
    val r0 = r(t0).map(_.asInstanceOf[PathRef])
    val r1 = r(t1).map(_.asInstanceOf[PathRef])
    val r2 = r(t2).map(_.asInstanceOf[PathRef])

    (r0, r1, r2) match {
      case (
            Result.Success(bundle),
            Result.Success(assetDir),
            Result.Success(sslKey)
          ) =>
        server.runBackground(
          bundle.path.toString,
          assetDir.path.toString,
          "8443",
          "8080",
          sslKey.path.toString,
          "password"
        )
    }

  }

  def run(ev: Evaluator) = T.command {
    val t0 = bundle
    val t1 = server.assetDir
    val t2 = server.localSslKey
    val r = ev.evaluate(SAgg[Task[_]](t0, t1, t2)).results
    val r0 = r(t0).map(_.asInstanceOf[PathRef])
    val r1 = r(t1).map(_.asInstanceOf[PathRef])
    val r2 = r(t2).map(_.asInstanceOf[PathRef])

    (r0, r1, r2) match {
      case (
            Result.Success(bundle),
            Result.Success(assetDir),
            Result.Success(sslKey)
          ) =>
        server.run(
          bundle.path.toString,
          assetDir.path.toString,
          "8443",
          "8080",
          sslKey.path.toString,
          "password"
        )
    }

  }

}

object ci extends WebModule {

  import ammonite.ops._
  import ammonite.ops.ImplicitWd._

  def mainJS = client.fullOpt
  def development = false
  def account: T[String] = "930183804331"
  def region: T[String] = "eu-west-2"
  def stack: T[String] = "Website"

  def sslPassword: T[String] = System.getenv("LSUG_SSL_PASSWORD")

  def devDependencies =
    Agg(
      "aws-cdk" -> "1.33.1"
    )

  def scripts = T.source {
    PathRef(millSourcePath / "scripts")
  }

  def synth = T {
    val out = T.ctx.dest
    os.copy.over(scripts().path, T.ctx.dest)
    val upload = out / "upload"
    mkdir(upload)
    os.copy(
      bundle().path,
      upload / "static"
    )

    os.copy.into(
      server.assetDir().path,
      upload
    )

    os.copy(
      server.assembly().path,
      upload / "app.jar"
    )

    os.write.over(
      out / "cdk.json",
      ujson
        .Obj(
          "app" ->
            List(
              "./stack.sc",
              stack(),
              account(),
              region(),
              upload.toString,
              sslPassword()
            ).mkString(" ")
        )
        .render(2)
    )
    yarn().%("cdk", "synth")(wd = T.ctx.dest)
    PathRef(out)
  }

  def diff() = T.command {
    val wd = synth()
    yarn().%("cdk", "diff")(wd = wd.path)
  }

  def bootstrap() = T.command {
    val wd = synth()
    yarn().%("cdk", "bootstrap", s"aws://${account()}/${region()}")(wd =
      wd.path
    )
  }

  def deploy() = T.command {
    val wd = synth()
    yarn().%("cdk", "deploy", "--require-approval", "never")(wd = wd.path)
  }

  def instanceId = T {
    $file.reboot.instanceId
  }

  def reboot() = T.command {
    $file.reboot.reboot(instanceId())
  }
}
