package com.cpuheater.bot.route

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.{Directives, Route, RouteResult}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpRequest
import com.cpuheater.bot.model.FBPObject
import com.cpuheater.bot.service.FBService
import com.cpuheater.bot.util.RouteSupport
import com.typesafe.scalalogging.LazyLogging
import com.cpuheater.bot.json.BotJson._

trait FBRoute extends Directives with LazyLogging with RouteSupport {

  protected implicit def actorSystem: ActorSystem

  protected implicit def ec: ExecutionContext

  private val fbService = FBService

  protected implicit val materializer: ActorMaterializer
  val fbRoute = {
    extractRequest { request: HttpRequest =>
      get {
        path("webhook") {
          parameters("hub.verify_token", "hub.mode", "hub.challenge") {
            (token, mode, challenge) =>
              complete {
                fbService.verifyToken(token, mode, challenge)
              }
          }
        }
      } ~
        post {
          path("webhook") {
            verifyPayload(request)(materializer, ec) {
              entity(as[FBPObject]) { fbObject =>
                complete {
                  fbService.handleMessage(fbObject)
                }
              }
            }
          }
        }
    }
  }
}


