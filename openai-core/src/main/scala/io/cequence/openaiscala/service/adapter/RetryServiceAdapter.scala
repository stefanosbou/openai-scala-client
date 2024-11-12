package io.cequence.openaiscala.service.adapter

import akka.actor.Scheduler
import io.cequence.openaiscala.{RetryHelpers, Retryable}
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.{ExecutionContext, Future}

private class RetryServiceAdapter[+S <: CloseableService](
  underlying: S,
  log: Option[String => Unit] = None,
  isRetryable: Throwable => Boolean
)(
  implicit ec: ExecutionContext,
  retrySettings: RetrySettings,
  scheduler: Scheduler
) extends ServiceWrapper[S]
    with CloseableService
    with FunctionNameHelper
    with RetryHelpers {

  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] =
    fun(underlying).retryOnFailure(
      Some(s"${getFunctionName().capitalize} call failed"),
      log,
      isRetryable
    )

  override def close(): Unit =
    underlying.close()
}
