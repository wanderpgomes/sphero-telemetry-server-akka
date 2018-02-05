package ca.wglabs.sphero.actor

import akka.actor.{Actor, ActorRef}
import ca.wglabs.sphero.model.{SpheroJoined, SpheroLeft, SpheroMeasurement, SpheroNotification}


case class Sphero(name: String, score: Int)
case class SpheroWithActor(sphero: Sphero, actor: ActorRef)
case class IncomingMeasurement(event: String)

class DrivingAreaActor extends Actor {

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

    case SpheroMeasurement(spheroName) => {
      println(s"Incoming message received for $spheroName: ")
      notifySphero(spheroName)
    }

  }

  def notifySphero(spheroName: String): Unit = {
    spheros(spheroName).actor ! SpheroNotification("red")
  }
}
