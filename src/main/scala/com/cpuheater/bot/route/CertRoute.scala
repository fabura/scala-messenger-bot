package com.cpuheater.bot.route

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.cpuheater.bot.util.RouteSupport
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

trait CertRoute extends Directives with LazyLogging with RouteSupport {

  protected implicit def actorSystem: ActorSystem

  protected implicit def ec: ExecutionContext

  protected implicit val materializer: ActorMaterializer

  val certRoute = {
    extractRequest { request: HttpRequest =>
      get {
        path(".well-known" / "acme-challenge" / "DVX_vapILImAkOn-IUBcD7meZCj6okzyKSnyK47_Kd4") {

          complete {
            "DVX_vapILImAkOn-IUBcD7meZCj6okzyKSnyK47_Kd4.47JVVLJeS-kggalg9bNrFin35CvxqT-hnnMSW3LLXh0"
          }
        }
      }
    }
  }
}


