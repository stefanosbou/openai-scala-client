package io.cequence.openaiscala.service

import akka.actor.Scheduler
import io.cequence.openaiscala.JsonFormats.eitherJsonSchemaFormat
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.{RetryHelpers, Retryable}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.domain.{BaseMessage, ChatRole, ModelId, UserMessage}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, Json}

import scala.concurrent.{ExecutionContext, Future}

object OpenAIChatCompletionExtra {

  protected val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val defaultMaxRetries = 5

  implicit class OpenAIChatCompletionImplicits(
    openAIChatCompletionService: OpenAIChatCompletionService
  ) extends RetryHelpers {

    def createChatCompletionWithFailover(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings,
      failoverModels: Seq[String],
      maxRetries: Option[Int] = Some(defaultMaxRetries),
      retryOnAnyError: Boolean = false,
      failureMessage: String
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[ChatCompletionResponse] = {
      val failoverSettings = failoverModels.map(model => settings.copy(model = model))
      val allSettingsInOrder = Seq(settings) ++ failoverSettings

      implicit val retrySettings: RetrySettings =
        RetrySettings(maxRetries = maxRetries.getOrElse(0))

      (openAIChatCompletionService
        .createChatCompletion(messages, _))
        .retryOnFailureOrFailover(
          // model is used only for logging
          normalAndFailoverInputsAndMessages =
            allSettingsInOrder.map(settings => (settings, settings.model)),
          failureMessage = Some(failureMessage),
          log = Some(logger.warn),
          isRetryable = isRetryable(retryOnAnyError)
        )
    }

    def createChatCompletionWithJSON[T: Format](
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings,
      failoverModels: Seq[String],
      maxRetries: Option[Int] = Some(defaultMaxRetries),
      retryOnAnyError: Boolean = false,
      taskNameForLogging: Option[String] = None
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[T] = {
      val start = new java.util.Date()

      val taskNameForLoggingFinal = taskNameForLogging.getOrElse("JSON-based chat-completion")

      val (messagesFinal, settingsFinal) = if (settings.jsonSchema.isDefined) {
        handleOutputJsonSchema(
          messages,
          settings,
          taskNameForLoggingFinal
        )
      } else {
        (messages, settings)
      }

      openAIChatCompletionService
        .createChatCompletionWithFailover(
          messagesFinal,
          settingsFinal,
          failoverModels,
          maxRetries,
          retryOnAnyError,
          failureMessage = s"${taskNameForLoggingFinal.capitalize} failed."
        )
        .map { response =>
          val content = response.choices.head.message.content
          val contentTrimmed = content.stripPrefix("```json").stripSuffix("```").trim
          val contentJson = contentTrimmed.dropWhile(_ != '{')
          val json = Json.parse(contentJson)

          logger.debug(
            s"${taskNameForLoggingFinal.capitalize} finished in " + (new java.util.Date().getTime - start.getTime) + " ms."
          )

          json.as[T]
        }
    }

    private def isRetryable(
      retryOnAnyError: Boolean
    ): Throwable => Boolean =
      if (retryOnAnyError) { _ =>
        true
      } else {
        case Retryable(_) => true
        case _            => false
      }
  }

  private val defaultJsonSchemaModels = Seq(
    "openai-" + ModelId.gpt_4o_2024_08_06,
    ModelId.gpt_4o_2024_08_06
  )

  def handleOutputJsonSchema(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings,
    taskNameForLogging: String,
    jsonSchemaModels: Seq[String] = defaultJsonSchemaModels
  ) = {
    val jsonSchemaDef = settings.jsonSchema.getOrElse(
      throw new IllegalArgumentException("JSON schema is not defined but expected.")
    )
    val jsonSchemaJson = Json.toJson(jsonSchemaDef.structure)
    val jsonSchemaString = Json.prettyPrint(jsonSchemaJson)

    val (settingsFinal, addJsonToPrompt) =
      if (jsonSchemaModels.contains(settings.model)) {
        logger.debug(
          s"Using OpenAI json schema mode for ${taskNameForLogging} and the model '${settings.model}' - name: ${jsonSchemaDef.name}, strict: ${jsonSchemaDef.strict}, structure:\n${jsonSchemaString}"
        )

        (
          settings.copy(
            response_format_type = Some(ChatCompletionResponseFormatType.json_schema)
          ),
          false
        )
      } else {
        // otherwise we failover to json object format and pass json schema to the user prompt

        logger.debug(
          s"Using JSON object mode for ${taskNameForLogging} and the model '${settings.model}'. Also passing a JSON schema as part of a user prompt."
        )

        (
          settings.copy(
            response_format_type = Some(ChatCompletionResponseFormatType.json_object),
            jsonSchema = None
          ),
          true
        )
      }

    val messagesFinal = if (addJsonToPrompt) {
      if (messages.nonEmpty && messages.last.role == ChatRole.User) {
        val outputJSONFormatAppendix =
          s"""
             |
             |<output_json_schema>
             |${jsonSchemaString}
             |</output_json_schema>""".stripMargin

        val newUserMessage = messages.last match {
          case x: UserMessage =>
            x.copy(
              content = x.content + outputJSONFormatAppendix
            )
          case _ => throw new IllegalArgumentException("Invalid message type")
        }

        logger.debug(s"Appended a JSON schema to a message:\n${newUserMessage.content}")

        messages.dropRight(1) :+ newUserMessage
      } else {
        val outputJSONFormatAppendix =
          s"""<output_json_schema>
             |${jsonSchemaString}
             |</output_json_schema>""".stripMargin

        logger.debug(
          s"Appended a JSON schema to an empty message:\n${outputJSONFormatAppendix}"
        )

        // need to create a new user message
        messages :+ UserMessage(outputJSONFormatAppendix)
      }
    } else {
      messages
    }

    (messagesFinal, settingsFinal)
  }
}
