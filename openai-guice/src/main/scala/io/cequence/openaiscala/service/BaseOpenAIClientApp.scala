package io.cequence.openaiscala.service

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import com.google.inject.Module

import scala.concurrent.{ExecutionContext, Future}

trait BaseOpenAIClientApp extends GuiceContainer with App {

  // modules
  override protected def modules: Seq[Module] = Seq(
    new ConfigModule(),
    new PekkoModule(),
    new ServiceModule()
  )

  protected val openAIService = instance[OpenAIService]

  // implicits
  protected implicit val system: ActorSystem = instance[ActorSystem]
  protected implicit val materializer: Materializer = instance[Materializer]
  protected implicit val executionContext: ExecutionContext =
    materializer.executionContext

  implicit protected class FutureOps[T](f: Future[T]) {
    def closeAndExit(): Unit =
      f.recover { case e: Exception =>
        e.printStackTrace()
        System.exit(1)
      }.onComplete { _ =>
        openAIService.close()
        system.terminate()
        System.exit(0)
      }
  }
}
