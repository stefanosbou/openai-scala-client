package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Example {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec = ExecutionContext.Implicits.global
  val service: OpenAIService = OpenAIServiceFactory() // sys.env("OPENAI_API_KEY")

  def main(args: Array[String]): Unit = {
    run.recover { case e: OpenAIScalaClientException =>
      e.printStackTrace()
      System.exit(1)
    }.onComplete { _ =>
      service.close()
      System.exit(0)
    }
  }

  protected def run: Future[_]

  protected def printMessageContent(response: ChatCompletionResponse) =
    println(response.choices.head.message.content)
}
