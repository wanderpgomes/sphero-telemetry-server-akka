package ca.wglabs.sphero.actor

import akka.actor.{Actor, ActorRef}
import ca.wglabs.sphero.model._

case class Sphero(name: String, var score: Int)
case class SpheroWithActor(sphero: Sphero, actor: ActorRef)



class SpheroTelemetryActor extends Actor {

  val incrementScore = 1
  val infractionScore = 40
  val velocityMax = 900.00

  val warningColor = "yellow"
  val infractionColor = "red"

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

    case SpheroMeasurement(spheroName, measurement) => {
      val velocity = calculateVelocity(measurement)
      if (velocity > velocityMax) {
        val sphero = spheros(spheroName).sphero
        sphero.score = sphero.score + incrementScore
        logMeasurement(sphero, velocity, measurement)

        if (sphero.score >= infractionScore) sendCommand(spheroName, infractionColor)
        else sendCommand(spheroName, warningColor)
      }
    }

    case _ => println("Invalid message")
  }

  def logMeasurement(sphero: Sphero, velocity: Double, measurement: Measurement): Unit ={
    println(s"Sphero: ${sphero.name} score: ${sphero.score}, velocity: $velocity, position (X, Y): (${measurement.positionX.value}, ${measurement.positionY.value}}")
  }

  def calculateVelocity(m: Measurement) : Double = {
    Math.sqrt((m.velocityX.value * m.velocityX.value) + (m.velocityY.value * m.velocityY.value))
  }

  def sendCommand(spheroName: String, color: String) = {
    spheros(spheroName).actor ! SpheroCommand(color)
  }
}
