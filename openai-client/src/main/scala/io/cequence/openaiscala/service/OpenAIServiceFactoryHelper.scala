package io.cequence.openaiscala.service

import org.apache.pekko.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import io.cequence.wsclient.ConfigImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

trait OpenAIServiceFactoryHelper[F] extends OpenAIServiceConsts {

  def apply(
    apiKey: String,
    orgId: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    val orgIdHeader = orgId.map(("OpenAI-Organization", _))
    val authHeaders = orgIdHeader ++: Seq(
      ("Authorization", s"Bearer $apiKey"),
      ("OpenAI-Beta", "assistants=v2")
    )

    customInstance(defaultCoreUrl, WsRequestContext(timeouts, authHeaders, Nil))
  }

  def apply(
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F =
    apply(ConfigFactory.load(configFileName))

  def apply(
    config: Config
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    def intTimeoutAux(fieldName: String) =
      config.optionalInt(s"$configPrefix.timeouts.${fieldName}Sec").map(_ * 1000)

    val timeouts = Timeouts(
      requestTimeout = intTimeoutAux("requestTimeout"),
      readTimeout = intTimeoutAux("readTimeout"),
      connectTimeout = intTimeoutAux("connectTimeout"),
      pooledConnectionIdleTimeout = intTimeoutAux("pooledConnectionIdleTimeout")
    )

    apply(
      apiKey = config.getString(s"$configPrefix.apiKey"),
      orgId = config.optionalString(s"$configPrefix.orgId"),
      timeouts = timeouts.toOption
    )
  }

  /**
   * Create an OpenAI Service for Azure using an API key.
   *
   * Note that not all endpoints are supported! Check <a
   * href="https://learn.microsoft.com/en-us/azure/cognitive-services/openai/reference">the
   * Azure OpenAI API documentation</a> for more information.
   *
   * @param resourceName
   *   The name of your Azure OpenAI Resource.
   * @param deploymentId
   *   The deployment name you chose when you deployed the model.
   * @param apiVersion
   *   The API version to use for this operation. This follows the YYYY-MM-DD format. Supported
   *   versions: 2023-03-15-preview, 2022-12-01, 2023-05-15, and 2023-06-01-preview
   */
  def forAzureWithApiKey(
    resourceName: String,
    deploymentId: String,
    apiVersion: String,
    apiKey: String,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    val authHeaders = Seq(("api-key", apiKey))
    forAzureAux(resourceName, deploymentId, apiVersion, authHeaders, timeouts)
  }

  /**
   * Create an OpenAI Service for Azure using an access token (Azure Active Directory
   * authentication).
   *
   * Note that not all endpoints are supported! Check <a
   * href="https://learn.microsoft.com/en-us/azure/cognitive-services/openai/reference">the
   * Azure OpenAI API documentation</a> for more information.
   *
   * @param resourceName
   *   The name of your Azure OpenAI Resource.
   * @param deploymentId
   *   The deployment name you chose when you deployed the model.
   * @param apiVersion
   *   The API version to use for this operation. This follows the YYYY-MM-DD format. Supported
   *   versions: 2023-03-15-preview, 2022-12-01, 2023-05-15, and 2023-06-01-preview
   */
  @Deprecated
  def forAzureWithAccessToken(
    resourceName: String,
    deploymentId: String,
    apiVersion: String,
    accessToken: String,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    val authHeaders = Seq(("Authorization", s"Bearer $accessToken"))
    forAzureAux(resourceName, deploymentId, apiVersion, authHeaders, timeouts)
  }

  private def forAzureAux(
    resourceName: String,
    deploymentId: String,
    apiVersion: String,
    authHeaders: Seq[(String, String)],
    timeouts: Option[Timeouts]
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    val coreUrl =
      s"https://${resourceName}.openai.azure.com/openai/deployments/${deploymentId}/"

    val extraParams = Seq("api-version" -> apiVersion)

    customInstance(
      coreUrl,
      WsRequestContext(
        timeouts,
        authHeaders,
        extraParams
      )
    )
  }

  def customInstance(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F
}

trait RawWsServiceFactory[F] {

  def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F
}
