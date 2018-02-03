package ca.wglabs.sphero

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import ca.wglabs.sphero.services.SpheroTelemetryService

import scala.io.StdIn

object Main extends App {

  implicit val actorSystem = ActorSystem("akka-system")
  implicit val flowMaterializer = ActorMaterializer()

  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = actorSystem.dispatcher


  val config = actorSystem.settings.config
  val interface = config.getString("app.interface")

  val port = config.getInt("app.port")

 val spheroTelemetryService = new SpheroTelemetryService()

  val binding = Http().bindAndHandle(spheroTelemetryService.route, interface, port)
  println(s"Server is now online at http://$interface:$port\nPress RETURN to stop...")

  StdIn.readLine()

  binding.flatMap(_.unbind()).onComplete(_ => actorSystem.terminate())
  println("Server is down...")
}
