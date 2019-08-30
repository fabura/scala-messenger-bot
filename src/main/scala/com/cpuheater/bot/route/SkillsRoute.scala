package com.cpuheater.bot.route

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.cpuheater.bot.model.{FBMessage, FBMessageEventOut, FBPObject, FBRecipient, Skills}
import com.cpuheater.bot.service.FBService
import com.cpuheater.bot.util.{HttpClient, RouteSupport}
import com.typesafe.scalalogging.LazyLogging
import com.cpuheater.bot.json.BotJson._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.ExecutionContext

trait SkillsRoute extends Directives with LazyLogging with RouteSupport {

  protected implicit def actorSystem: ActorSystem

  protected implicit def ec: ExecutionContext

  protected implicit val materializer: ActorMaterializer

  val skillsRoute = {
    extractRequest { request: HttpRequest =>
      post {
        path("skills") {
          logger.info(request.toString())
          entity(as[Skills]) { skills =>
            logger.info(skills.toString)
            HttpClient.post(FBMessageEventOut(recipient = FBRecipient(skills.psid),
              message = FBMessage(text = Some(
                "Он прочел все мои письма к тебе!"))))
            complete {
              "Ok"
            }
          }
        }
      }
    }
  }
}



