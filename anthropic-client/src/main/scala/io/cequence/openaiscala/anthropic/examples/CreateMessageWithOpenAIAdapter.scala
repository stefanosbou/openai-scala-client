package io.cequence.openaiscala.anthropic.examples

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

object CreateMessageWithOpenAIAdapter extends ExampleBase[OpenAIChatCompletionService] {

  val service: OpenAIChatCompletionService = AnthropicServiceFactory.asOpenAI()

  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_2_1)
      )
      .map { content =>
        println(content.choices.headOption.map(_.message.content).getOrElse("N/A"))
      }
}