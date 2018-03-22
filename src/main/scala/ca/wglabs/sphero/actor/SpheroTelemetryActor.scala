package ca.wglabs.sphero.actor

import akka.actor.{Actor, ActorRef, ActorSystem}
import ca.wglabs.sphero.model._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class Sphero(name: String, var score: Int = 0, var exempt: Boolean = false)
case class SpheroWithActor(sphero: Sphero, actor: ActorRef)



class SpheroTelemetryActor extends Actor {

  val system = ActorSystem("akka-system")

  val spheros = collection.mutable.LinkedHashMap[String, SpheroWithActor]()

  override def receive: Receive = {

    case DeviceJoined(spheroName, actor) => {
      spheros += (spheroName -> SpheroWithActor(Sphero(spheroName), actor))
      println(s"Sphero joined: $spheroName")
    }

    case DeviceLeft(spheroName) => {
      spheros -= spheroName
      println(s"Sphero left: $spheroName")
    }

    case VelocityInfraction(spheroName, velocity, position, date) => {
      val sphero = spheros(spheroName).sphero
      if (!sphero.exempt) {
        sphero.score += 1
        sphero.exempt = true
        if (sphero.score > 1) sendCommand(spheroName, "red")
        else sendCommand(spheroName, "yellow")

        system.scheduler.scheduleOnce(10 seconds, self, InfractionExempt(sphero.name, false))
        println(s"Infraction detected for device $spheroName: velocity=$velocity, date=$date")
      } else {
        sendCommand(spheroName, "yellow")
        println(s"Infraction detected for device $spheroName, but it is temporarily exempt.")
      }

    }

    case InfractionExempt(spheroName, exempt) => {
      val sphero = spheros(spheroName).sphero
      sphero.exempt = exempt
    }

    case _ => println("Invalid message")
  }


  def sendCommand(spheroName: String, color: String) = {
    spheros(spheroName).actor ! DeviceCommand(color)
  }
}
