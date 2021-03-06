package scalajsbundler.util

import org.scalajs.ir.Trees.JSNativeLoadSpec

import org.scalajs.linker._
import org.scalajs.linker.interface._
import org.scalajs.linker.standard._
import org.scalajs.linker.scalajsbundler.StoreLinkingUnitLinkerBackend
import org.scalajs.sbtplugin.Loggers
import sbt.Logger

import scala.concurrent.duration.Duration
import scala.concurrent.Await

object ScalaJSOutputAnalyzer {

  /**
    * @return The list of ES modules imported by a Scala.js project
    * @param linkingUnit The Scala.js linking unit
    */
  def importedModules(linkingUnit: LinkingUnit): List[String] =
    linkingUnit.classDefs
      .flatMap(_.jsNativeLoadSpec)
      .flatMap {
        case JSNativeLoadSpec.Import(module, _) => List(module)
        case JSNativeLoadSpec.ImportWithGlobalFallback(
            JSNativeLoadSpec.Import(module, _),
            _) =>
          List(module)
        case JSNativeLoadSpec.Global(_, _) => Nil
      }
      .distinct

  /**
    * Extract the linking unit from the Scala.js output
    *
    * @param linkerConfig Configuration of the Scala.js linker
    * @param linker Scala.js linker
    * @param irFiles Scala.js IR files
    * @param moduleInitializers Scala.js module initializers
    * @param logger Logger
    * @return
    */
  def linkingUnit(
      linkerConfig: StandardConfig,
      linker: ClearableLinker,
      irFiles: Seq[IRFile],
      moduleInitializers: Seq[ModuleInitializer],
      logger: Logger
  ): LinkingUnit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    require(linkerConfig.moduleKind == ModuleKind.CommonJSModule,
            s"linkerConfig.moduleKind was ${linkerConfig.moduleKind}")
    val frontend = StandardLinkerFrontend(linkerConfig)
    val backend = new StoreLinkingUnitLinkerBackend(linkerConfig)
    val linker = StandardLinkerImpl(frontend, backend)
    val dummyOutput = LinkerOutput(MemOutputFile())
    val future = linker.link(irFiles, moduleInitializers, dummyOutput, Loggers.sbtLogger2ToolsLogger(logger))
    concurrent.blocking(Await.result(future, Duration.Inf))
    backend.outputLinkingUnit
  }

}
