package ca.wglabs.telemetry.actor

import akka.actor.{Actor, ActorRef, ActorSystem}
import ca.wglabs.telemetry.model._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class Device(name: String, var score: Int = 0, var exempt: Boolean = false)
case class DeviceWithActor(device: Device, actor: ActorRef)



class InfractionActor extends Actor {

  val system = ActorSystem("akka-system")

  val devices = collection.mutable.LinkedHashMap[String, DeviceWithActor]()

  override def receive: Receive = {

    case DeviceJoined(deviceName, actor) => {
      devices += (deviceName -> DeviceWithActor(Device(deviceName), actor))
      println(s"Device joined: $deviceName")
    }

    case DeviceLeft(deviceName) => {
      devices -= deviceName
      println(s"Device left: $deviceName")
    }

    case VelocityInfraction(deviceName, velocity, position, date) => {
      val device = devices(deviceName).device
      if (!device.exempt) {
        device.score += 1
        device.exempt = true
        if (device.score > 1) sendCommand(deviceName, "red")
        else sendCommand(deviceName, "yellow")

        system.scheduler.scheduleOnce(10 seconds, self, InfractionExempt(device.name, false))
        println(s"Infraction detected for device $deviceName: velocity=$velocity, date=$date")
      } else {
        sendCommand(deviceName, "yellow")
        println(s"Infraction detected for device $deviceName, but it is temporarily exempt.")
      }

    }

    case InfractionExempt(deviceName, exempt) => {
      val device = devices(deviceName).device
      device.exempt = exempt
    }

    case _ => println("Invalid message")
  }


  def sendCommand(deviceName: String, color: String) = {
    devices(deviceName).actor ! DeviceCommand(color)
  }
}
