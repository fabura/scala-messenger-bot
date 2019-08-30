package com.cpuheater.bot

import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.javadsl.model.headers.RawHeader
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.cpuheater.bot.route.{FBRoute, SkillsRoute}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._


object BotApp extends App with FBRoute with SkillsRoute with LazyLogging {

  val decider: Supervision.Decider = { e =>
    logger.error(s"Exception in stream $e")
    Supervision.Stop
  }

  implicit val actorSystem: ActorSystem = ActorSystem("bot", ConfigFactory.load)
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(
    actorSystem).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)

  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher

  val routes = {
    skillsRoute ~
      respondWithHeaders(
        headers.RawHeader("X-Frame-Options", "ALLOW-FROM https://www.messenger.com/"),
        headers.RawHeader("X-Frame-Options", "ALLOW-FROM https://www.facebook.com/")
      ) {
        getFromResourceDirectory("web")
      } ~ logRequestResult("bot") {
      fbRoute
    }
  }

  implicit val timeout = Timeout(30.seconds)

  val routeLogging = DebuggingDirectives.logRequestResult("RouteLogging", Logging.DebugLevel)(
    routes)

  // do not store passwords in code, read them from somewhere safe!
  val password = "".toCharArray

  val port: Int = sys.env.getOrElse("PORT", "8080").toInt

  Http().bindAndHandle(routeLogging, "0.0.0.0", port)
  logger.info(s"HTTP bound to 0.0.0.0:$port")
  logger.info("Starting")

}
