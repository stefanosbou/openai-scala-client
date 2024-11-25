package io.cequence.openaiscala.service

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionChunkResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import java.util.concurrent.atomic.AtomicInteger

// TODO: use wrappers/adapters instead
object OpenAIChatCompletionStreamedRoundRobinService {
  def apply(
    services: Seq[OpenAIChatCompletionStreamedServiceExtra]
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedRoundRobinServiceImpl(services)

  final private class OpenAIChatCompletionStreamedRoundRobinServiceImpl(
    underlyings: Seq[OpenAIChatCompletionStreamedServiceExtra]
  ) extends OpenAIChatCompletionStreamedServiceExtra {

    private val count = underlyings.size
    private val atomicCounter = new AtomicInteger()

    private def calcIndex: Int =
      atomicCounter.getAndUpdate(index => (index + 1) % count)

    private def getService = underlyings(calcIndex)

    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Source[ChatCompletionChunkResponse, NotUsed] =
      getService.createChatCompletionStreamed(
        messages,
        settings
      )

    override def close(): Unit =
      underlyings.foreach(_.close())
  }
}
