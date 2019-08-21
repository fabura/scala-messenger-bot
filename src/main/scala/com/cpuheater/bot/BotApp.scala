package com.cpuheater.bot

import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.cpuheater.bot.route.{CertRoute, FBRoute}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object BotApp extends App with FBRoute with CertRoute with LazyLogging {

  val decider: Supervision.Decider = { e =>
    logger.error(s"Exception in stream $e")
    Supervision.Stop
  }

  implicit val actorSystem: ActorSystem = ActorSystem("bot", ConfigFactory.load)
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(actorSystem).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)

  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher

  val routes = {
    logRequestResult("bot") {
      fbRoute
    } ~ certRoute
  }

  implicit val timeout = Timeout(30.seconds)

  val routeLogging = DebuggingDirectives.logRequestResult("RouteLogging", Logging.DebugLevel)(routes)

  // do not store passwords in code, read them from somewhere safe!
  val password = "".toCharArray

  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystore = getClass.getClassLoader.getResourceAsStream("fullchain.p12")

  require(keystore != null, "Keystore required!")
  ks.load(keystore, password)

  val kmf = KeyManagerFactory.getInstance("SunX509")
  kmf.init(ks, password)

  val tmf = TrustManagerFactory.getInstance("SunX509")
  tmf.init(ks)

  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers, new SecureRandom)

  val port: Int = sys.env.getOrElse("PORT", "8080").toInt

  Http().bindAndHandle(
    handler = routeLogging,
    interface = "0.0.0.0",
    port = port,
    connectionContext = ConnectionContext.https(sslContext)
  ) map { binding =>
    logger.info(s"HTTPD bound to ${binding.localAddress}")
  } recover { case ex =>
    logger.info(s"HTTPS could not bind", ex.getMessage)
  }

//  Http().bindAndHandle(routeLogging, "0.0.0.0", 8080)
  logger.info("Starting")

}
