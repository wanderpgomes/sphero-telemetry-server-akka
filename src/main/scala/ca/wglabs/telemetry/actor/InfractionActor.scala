package ca.wglabs.telemetry.actor

import akka.actor.{Actor, ActorRef, ActorSystem}
import ca.wglabs.telemetry.model._
import ca.wglabs.telemetry.services.constants.{firstInfractionColor, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class Device(name: String, var points: Int = 0, var isExempt: Boolean = false)
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
      if (device.isExempt) {
        println(s"Velocity Infraction detected for device name=$deviceName," +
          s" but it is exempt for $infractionExemptionPeriod seconds")
      } else {
        sendDeviceCommand(device)
        updateDeviceState(device)
        system.scheduler.scheduleOnce(infractionExemptionPeriod seconds, self, InfractionExempt(device.name, false))
        println(s"Velocity Infraction detected for device" +
          s" name=$deviceName," +
          s" velocity=${velocity.v} ${velocity.unit}," +
          s" position=(${position.x}, ${position.y}," +
          s" date=$date")
      }
    }

    case InfractionExempt(deviceName, exempt) => {
      val device = devices(deviceName).device
      device.isExempt = exempt
    }

    case _ => println("Invalid message")
  }

  def isFirstInfraction(device: Device) = device.points equals 0

  def updateDeviceState(device: Device) = {
    device.points += 1
    device.isExempt = true
  }

  def sendDeviceCommand(device: Device) = {
    devices(device.name).actor !
      DeviceCommand(if (isFirstInfraction(device)) firstInfractionColor else repeatedInfractionColor)
  }
}
