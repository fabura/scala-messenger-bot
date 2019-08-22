package com.cpuheater.bot.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.cpuheater.bot.model
import com.cpuheater.bot.model.{FBMessage, FBMessageEventOut, FBPostback, FBRecipient}
import com.cpuheater.bot.util.HttpClient
import com.cpuheater.bot.json.BotJson._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object ValyaBotService extends LazyLogging {
  def handleMessage(me: model.FBMessageEventIn)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Unit = {
    val senderId = me.sender.id
    me.postback.foreach(handlePostBack(senderId, _))
    me.message.foreach(handleMessage(senderId, _))
  }

  def handlePostBack(senderId: String, postback: FBPostback)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Unit = {
    postback.payload match {
      case Some("start") =>
        val response = FBMessageEventOut(recipient = FBRecipient(senderId),
          message = FBMessage(text = Some(s"Введите имя и фамилию"),
            metadata = Some("lol")))
        HttpClient.post(response)
      case Some(_) => logger.debug(s"Unknown postback: $postback")
      case None => logger.debug(s"Empty payload: $postback")
    }
  }

  def handleMessage(senderId: String, message: FBMessage)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Unit = {
    message.text match {
      case Some(text) =>
        val fbMessage = FBMessageEventOut(recipient = FBRecipient(senderId),
          message = FBMessage(text = Some(s"Scala messenger bot: $text"),
            metadata = Some("DEVELOPER_DEFINED_METADATA")))
        HttpClient.post(fbMessage).map(_ => ())
      case None => logger.info("No text in message")
    }
  }
}

//
//trait BotStep[F[_]] {
//  val id: StepId
//
//  def nextId: StepId
//
//  def action: FBMessage => F[Unit]
//}
//
//case class Step[F[_]](id: StepId, nextId: StepId, action: FBMessage => F[FBMessage]) extends BotStep[F]
//
//object Step {
//  type StepId = String
//
//  val flow = Seq(
//    Step("1", "2",)
//  )
//}
//
