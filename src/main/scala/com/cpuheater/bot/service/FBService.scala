package com.cpuheater.bot.service

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.{HttpHeader, StatusCode, StatusCodes}
import akka.stream.ActorMaterializer
import com.cpuheater.bot.BotConfig
import com.cpuheater.bot.model._
import com.cpuheater.bot.util.HttpClient
import com.typesafe.scalalogging.LazyLogging
import com.cpuheater.bot.json.BotJson._

import scala.concurrent.{ExecutionContext, Future}
import spray.json._

object FBService extends LazyLogging {

  def verifyToken(token: String, mode: String, challenge: String)
                 (implicit ec: ExecutionContext):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {

    if (mode == "subscribe" && token == BotConfig.fb.verifyToken) {
      logger.info(s"Verify webhook token: ${token}, mode ${mode}")
      (StatusCodes.OK, List.empty[HttpHeader], Some(Left(challenge)))
    }
    else {
      logger.error(s"Invalid webhook token: ${token}, mode ${mode}")
      (StatusCodes.Forbidden, List.empty[HttpHeader], None)
    }
  }

  def handleMessage(fbObject: FBPObject)
                   (implicit ec: ExecutionContext, system: ActorSystem,
                    materializer: ActorMaterializer):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {

    logger.info(s"Receive fbObject: $fbObject")
    fbObject.entry.foreach {
      entry =>
        entry.messaging.foreach { me =>
          Future.successful(())
          ValyaBotService.handleMessage(me)
        }
    }
    (StatusCodes.OK, List.empty[HttpHeader], None)
  }

}
