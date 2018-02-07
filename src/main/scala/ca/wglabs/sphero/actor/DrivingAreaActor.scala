package ca.wglabs.sphero.actor

import akka.actor.{Actor, ActorRef}
import ca.wglabs.sphero.model.{SpheroJoined, SpheroLeft, SpheroMeasurementReceived, SpheroNotified}

case class Sphero(name: String, score: Int)
case class SpheroWithActor(sphero: Sphero, actor: ActorRef)

case class Measurement(xVelocity: Velocity, yVelocity: Velocity)
case class Velocity(units: String, value: List[Int])

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

    case SpheroMeasurementReceived(spheroName, measurement) => {
      println(s"Incoming message received for $spheroName: xVelocity=${measurement.xVelocity.value(0)} yVelocity=${measurement.yVelocity.value(0)}")
      if (measurement.yVelocity.value(0) > 3) notifySphero(spheroName)
    }

    case _ => println("Invalid Sphero Event")

  }

  def notifySphero(spheroName: String): Unit = {
    spheros(spheroName).actor ! SpheroNotified("red")
  }
}
