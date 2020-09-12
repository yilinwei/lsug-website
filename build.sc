import $file.webpack
// import $file.aws

import mill._
import scalalib._
import scalafmt._
import mill.scalajslib._
import webpack.{WebpackModule, NpmDependency}

import $ivy.`com.goyeau::mill-scalafix:9433263`
import com.goyeau.mill.scalafix.ScalafixModule

val catsEffectDep = ivy"org.typelevel::cats-effect::2.1.0"

val monocleDeps = Agg(
  "monocle-core",
  "monocle-macro"
).map { dep => ivy"com.github.julien-truffaut::${dep}::2.0.1" }

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
    "UTF-8"
  )

trait ProtocolModule extends ScalaModule {

  def scalaVersion = "2.13.1"
  def scalacOptions = commonScalacOptions

  def ivyDeps =
    Agg(ivy"org.typelevel::cats-core::2.1.0") ++ Agg(
      "circe-core",
      "circe-parser",
      "circe-generic"
    ).map { dep => ivy"io.circe::${dep}::0.13.0" }

  def millSourcePath = build.millSourcePath / "protocol"

}

object protocolJs extends ProtocolModule with ScalaJSModule {
  def scalaJSVersion = "0.6.32"
}

object protocolJvm extends ProtocolModule

object server extends ScalaModule with ScalafixModule with ScalafmtModule {

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
      ivy"io.chrisdavenport::log4cats-slf4j::1.0.1",
      ivy"ch.qos.logback:logback-classic:1.2.3"
    ) ++ Agg(
      "http4s-dsl",
      "http4s-circe",
      "http4s-blaze-server",
      "http4s-blaze-client"
    ).map { dep => ivy"org.http4s::${dep}::0.21.3" } ++ Agg(
      "tapir-core",
      "tapir-json-circe",
      "tapir-http4s-server"
    ).map { dep => ivy"com.softwaremill.sttp.tapir::${dep}::0.16.16"} ++ Agg(
      "fs2-io",
      "fs2-core"
    ).map { dep => ivy"co.fs2::${dep}::2.3.0" } ++ monocleDeps ++
    Agg(ivy"io.chrisdavenport::cats-time::0.3.4")

  def assetDir = T.source {
    millSourcePath / "src" / "main" / "resources"
  }

  object test extends Tests {
    def ivyDeps = Agg(ivy"org.scalameta::munit::0.7.1")
    def testFrameworks = Seq("munit.Framework")
  }
}

object client extends ScalaJSModule {
  def scalaVersion = "2.13.1"
  def scalaJSVersion = "0.6.29"
  def scalacPluginIvyDeps =
    super.scalacPluginIvyDeps() ++ Agg(
      ivy"com.olegpy:better-monadic-for_2.13:0.3.1"
    )
  def scalacOptions = commonScalacOptions
  def moduleDeps = Seq(protocolJs)
  def ivyDeps =
    monocleDeps ++ Agg(
      ivy"io.github.cquiroz::scala-java-time::2.0.0-RC5",
      catsEffectDep
    ) ++ Agg(
      "core",
      "extra",
      "ext-cats"
    ).map { dep => ivy"com.github.japgolly.scalajs-react::${dep}::1.6.0" }
}

trait WebModule extends WebpackModule {

  def webpackVersion = "4.17.1"
  def webpackCliVersion = "3.1.0"
  def webpackDevServerVersion = "3.1.7"
  def sassVersion = "1.25.0"
  def npmDeps =
    Agg("react" -> "React", "react-dom" -> "ReactDOM").map {
      case (n, g) => NpmDependency(n, "16.7.0", g)
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
    val r = ev.evaluate(SAgg[Task[_]](t0, t1)).results
    val r0 = r(t0).map(_.asInstanceOf[PathRef])
    val r1 = r(t1).map(_.asInstanceOf[PathRef])

    (r0, r1) match {
      case (Result.Success(bundle), Result.Success(assetDir)) =>
        server.runBackground(bundle.path.toString, assetDir.path.toString)
    }

  }

  def run(ev: Evaluator) = T.command {
    val t0 = bundle
    val t1 = server.assetDir
    val r = ev.evaluate(SAgg[Task[_]](t0, t1)).results
    val r0 = r(t0).map(_.asInstanceOf[PathRef])
    val r1 = r(t1).map(_.asInstanceOf[PathRef])

    (r0, r1) match {
      case (Result.Success(bundle), Result.Success(assetDir)) =>
        server.run(bundle.path.toString, assetDir.path.toString)
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
              upload.toString
            ).mkString(" ")
        )
        .render(2)
    )
    // This is ludicrous, but tht's how the cdk works...
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

}
