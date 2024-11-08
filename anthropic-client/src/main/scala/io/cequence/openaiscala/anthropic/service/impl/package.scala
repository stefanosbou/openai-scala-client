package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.CacheControl.Ephemeral
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.TextBlock
import io.cequence.openaiscala.anthropic.domain.Content.{ContentBlockBase, ContentBlocks}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.{CacheControl, Content, Message}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChunkMessageSpec,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  ChatRole,
  MessageSpec,
  SystemMessage,
  BaseMessage => OpenAIBaseMessage,
  Content => OpenAIContent,
  ImageURLContent => OpenAIImageContent,
  TextContent => OpenAITextContent,
  UserMessage => OpenAIUserMessage,
  UserSeqMessage => OpenAIUserSeqMessage
}

import java.{util => ju}

package object impl extends AnthropicServiceConsts {

  val AnthropicCacheControl = "cache_control"

  def toAnthropicMessages(
    messages: Seq[OpenAIBaseMessage],
    settings: CreateChatCompletionSettings
  ): Seq[Message] = {
    // send settings, cache_system, cache_user, // cache_tools_definition
    // TODO: handle other message types (e.g. assistant)
    val useSystemCache: Option[CacheControl] =
      if (settings.useAnthropicSystemMessagesCache) Some(Ephemeral) else None
    val countUserMessagesToCache = settings.anthropicCachedUserMessagesCount

    def onlyOnceCacheControl(cacheUsed: Boolean): Option[CacheControl] =
      if (cacheUsed) None else useSystemCache

    // cacheSystemMessages
    // cacheUserMessages - number of user messages to cache (1-4) (1-3). 1

//    (system, user) => 1x system, 3x user
//    (_, user) => 4x user
//    (system, _) => 1x system

    // construct Anthropic messages out of OpenAI messages
    // the first N user messages are marked as cached, where N is equal to countUserMessagesToCache
    // if useSystemCache is true, the last system message is marked as cached

    // so I need to keep track, while foldLefting, of the number of user messages we are still able to cache

    messages
      .foldLeft((List.empty[Message], countUserMessagesToCache): (List[Message], Int)) {
        case ((acc, userMessagesToCache), message) =>
          message match {
            case OpenAIUserMessage(content, _) =>
              val cacheControl = if (userMessagesToCache > 0) Some(Ephemeral) else None
              (
                acc :+ Message.UserMessage(content, cacheControl),
                userMessagesToCache - cacheControl.map(_ => 1).getOrElse(0)
              )
            case OpenAIUserSeqMessage(contents, _) => {
              val (contentBlocks, remainingCache) =
                contents.foldLeft((Seq.empty[ContentBlockBase], userMessagesToCache)) {
                  case ((acc, cacheLeft), content) =>
                    val (block, newCacheLeft) = toAnthropic(cacheLeft)(content)
                    (acc :+ block, newCacheLeft)
                }
              (acc :+ Message.UserMessageContent(contentBlocks), remainingCache)
            }

          }
      }
      ._1

  }

  def toAnthropic(userMessagesToCache: Int)(content: OpenAIContent)
    : (Content.ContentBlockBase, Int) = {
    val cacheControl = if (userMessagesToCache > 0) Some(Ephemeral) else None
    val newCacheControlCount = userMessagesToCache - cacheControl.map(_ => 1).getOrElse(0)
    content match {
      case OpenAITextContent(text) =>
        (ContentBlockBase(TextBlock(text), cacheControl), newCacheControlCount)

      case OpenAIImageContent(url) =>
        if (url.startsWith("data:")) {
          val mediaTypeEncodingAndData = url.drop(5)
          val mediaType = mediaTypeEncodingAndData.takeWhile(_ != ';')
          val encodingAndData = mediaTypeEncodingAndData.drop(mediaType.length + 1)
          val encoding = mediaType.takeWhile(_ != ',')
          val data = encodingAndData.drop(encoding.length + 1)
          ContentBlockBase(
            Content.ContentBlock.ImageBlock(encoding, mediaType, data),
            cacheControl
          ) -> newCacheControlCount
        } else {
          throw new IllegalArgumentException(
            "Image content only supported by providing image data directly."
          )
        }
    }
  }

  def toAnthropic(
    settings: CreateChatCompletionSettings,
    messages: Seq[OpenAIBaseMessage]
  ): AnthropicCreateMessageSettings = {
    def systemMessagesContent = messages.collect { case SystemMessage(content, _) =>
      content
    }.mkString("\n")

    AnthropicCreateMessageSettings(
      model = settings.model,
      system = if (systemMessagesContent.isEmpty) None else Some(systemMessagesContent),
      max_tokens = settings.max_tokens.getOrElse(DefaultSettings.CreateMessage.max_tokens),
      metadata = Map.empty,
      stop_sequences = settings.stop,
      temperature = settings.temperature,
      top_p = settings.top_p,
      top_k = None
    )
  }

  def toOpenAI(response: CreateMessageResponse): ChatCompletionResponse =
    ChatCompletionResponse(
      id = response.id,
      created = new ju.Date(),
      model = response.model,
      system_fingerprint = response.stop_reason,
      choices = Seq(
        ChatCompletionChoiceInfo(
          message = toOpenAIAssistantMessage(response.content),
          index = 0,
          finish_reason = response.stop_reason,
          logprobs = None
        )
      ),
      usage = Some(toOpenAI(response.usage))
    )

  def toOpenAI(blockDelta: ContentBlockDelta): ChatCompletionChunkResponse =
    ChatCompletionChunkResponse(
      id = "",
      created = new ju.Date,
      model = "",
      system_fingerprint = None,
      choices = Seq(
        ChatCompletionChoiceChunkInfo(
          delta = ChunkMessageSpec(
            role = None,
            content = Some(blockDelta.delta.text)
          ),
          index = blockDelta.index,
          finish_reason = None
        )
      ),
      usage = None
    )

  def toOpenAIAssistantMessage(content: ContentBlocks): AssistantMessage = {
    val textContents = content.blocks.collect { case ContentBlockBase(TextBlock(text), _) =>
      text
    } // TODO
    // TODO: log if there is more than one text content
    if (textContents.isEmpty) {
      throw new IllegalArgumentException("No text content found in the response")
    }
    val singleTextContent = concatenateMessages(textContents)
    AssistantMessage(singleTextContent, name = None)
  }

  private def concatenateMessages(messageContent: Seq[String]): String =
    messageContent.mkString("\n")

  def toOpenAI(usageInfo: UsageInfo): OpenAIUsageInfo = {
    OpenAIUsageInfo(
      prompt_tokens = usageInfo.input_tokens,
      total_tokens = usageInfo.input_tokens + usageInfo.output_tokens,
      completion_tokens = Some(usageInfo.output_tokens)
    )
  }
}
