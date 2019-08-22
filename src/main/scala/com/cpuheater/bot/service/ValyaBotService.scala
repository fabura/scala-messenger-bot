package com.cpuheater.bot.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.cpuheater.bot.model
import com.cpuheater.bot.model.{FBMessage, FBMessageEventOut, FBPostback, FBRecipient}
import com.cpuheater.bot.util.HttpClient
import com.cpuheater.bot.json.BotJson._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object ValyaBotService {
  def handleMessage(me: model.FBMessageEventIn)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Unit = {
    val senderId = me.sender.id
    me.postback match {
      case Some(FBPostback(_, Some("start"))) =>
        val response = FBMessageEventOut(recipient = FBRecipient(senderId),
          message = FBMessage(text = Some(s"Привет, пидр!"),
            metadata = Some("lol")))
        HttpClient.post(response).map(_ => ())
    }
    me.message match {
      case Some(FBMessage(_, _, Some(text), _, _)) =>
        val fbMessage = FBMessageEventOut(recipient = FBRecipient(senderId),
          message = FBMessage(text = Some(s"Scala messenger bot: $text"),
            metadata = Some("DEVELOPER_DEFINED_METADATA")))
        HttpClient.post(fbMessage).map(_ => ())
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
