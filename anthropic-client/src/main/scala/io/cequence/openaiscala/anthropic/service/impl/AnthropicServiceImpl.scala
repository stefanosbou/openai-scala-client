package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.anthropic.JsonFormats
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.{ChatRole, Message}
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.service.ws.WSRequestExtHelper
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Future

private[service] trait AnthropicServiceImpl
    extends AnthropicService
    with WSRequestExtHelper
    with JsonFormats {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createMessage(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Future[CreateMessageResponse] =
    execPOST(
      EndPoint.messages,
      bodyParams = createBodyParamsForMessageCreation(messages, settings, stream = false)
    ).map(
      _.asSafe[CreateMessageResponse]
    )

  protected def createBodyParamsForMessageCreation(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messages.nonEmpty, "At least one message expected.")
    assert(messages(0).role == ChatRole.User, "First message must be from user.")

    val messageJsons = messages.map(Json.toJson(_))

    jsonBodyParams(
      Param.messages -> Some(messageJsons),
      Param.model -> Some(settings.model),
      Param.system -> settings.system,
      Param.max_tokens -> Some(settings.max_tokens),
      Param.metadata -> { if (settings.metadata.isEmpty) None else Some(settings.metadata) },
      Param.stop_sequences -> {
        if (settings.stop_sequences.nonEmpty) Some(settings.stop_sequences) else None
      },
      Param.stream -> Some(stream),
      Param.temperature -> settings.temperature,
      Param.top_p -> settings.top_p,
      Param.top_k -> settings.top_k
    )
  }
}