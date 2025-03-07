package com.cpuheater.bot.util

import java.io.IOException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import com.cpuheater.bot.BotConfig
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object HttpClient extends LazyLogging {

  def post(uri: String, body: Array[Byte])(implicit ec: ExecutionContext, system: ActorSystem): Future[Unit] = {
    val entity = HttpEntity(MediaTypes.`application/json`, body)
    val response: Future[HttpResponse] = Http().singleRequest(HttpRequest(HttpMethods.POST, Uri(uri), entity = entity))

    val result = response.flatMap {
      response =>
        response.status match {
          case status if status.isSuccess =>
            Future.successful()
          case _ =>
            Future.successful(throw new IOException(s"Token request failed with status ${response.status} and error ${response.entity}"))
        }
    }
    result.onComplete {
      case Success(response) =>
        logger.info(s"Success after sending response $response")
      case Failure(ex) =>
        logger.info(s"Failure after sending response to: $uri $ex")
    }
    result

  }

  def post[T: JsonWriter](fbMessage: T)(implicit ec: ExecutionContext, system: ActorSystem): Future[Unit] =
    post(s"${BotConfig.fb.responseUri}?access_token=${BotConfig.fb.pageAccessToken}", fbMessage.toJson.toString().getBytes)


}

