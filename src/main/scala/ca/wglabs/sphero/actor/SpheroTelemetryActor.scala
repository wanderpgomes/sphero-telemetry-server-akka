package ca.wglabs.sphero.actor

import akka.actor.{Actor, ActorRef}
import ca.wglabs.sphero.model._

case class Sphero(name: String, var score: Int)
case class SpheroWithActor(sphero: Sphero, actor: ActorRef)



class SpheroTelemetryActor extends Actor {


  val spheros = collection.mutable.LinkedHashMap[String, SpheroWithActor]()

  override def receive: Receive = {

    case SpheroJoined(spheroName, actor) => {
      spheros += (spheroName -> SpheroWithActor(Sphero(spheroName, 0), actor))
      println(s"Sphero joined: $spheroName")
    }

    case SpheroLeft(spheroName) => {
      spheros -= spheroName
      println(s"Sphero left: $spheroName")
    }

    case InfractionDetected(spheroName, velocity, position, date) => {

      println("Infraction detected!!")
      sendCommand(spheroName, "red")
    }

    case _ => println("Invalid message")
  }


  def sendCommand(spheroName: String, color: String) = {
    spheros(spheroName).actor ! SpheroCommand(color)
  }
}
