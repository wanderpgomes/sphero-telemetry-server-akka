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

    case InfractionExempted(deviceName, exempt) => {
      val device = devices(deviceName).device
      device.isExempt = exempt
    }

    case infraction : Infraction => {
      handleInfraction(infraction)
    }

    case _ => println("Invalid device event")
  }

  def handleInfraction(infraction: Infraction) = {
    val device = devices(infraction.deviceName).device
    if (!device.isExempt) {
      sendDeviceCommand(device)
      updateDeviceState(device)
      logInfraction(infraction)

      system.scheduler.scheduleOnce(infractionExemptionPeriod seconds, self, InfractionExempted(device.name, false))

    } else logInfractionExempt(infraction)
  }

  def isFirstInfraction(device: Device) = device.points equals 0

  def updateDeviceState(device: Device) = {
    device.points += 1
    device.isExempt = true
  }

  def sendDeviceCommand(device: Device) = {
    devices(device.name).actor !
      DeviceResponse(if (isFirstInfraction(device)) firstInfractionColor else repeatedInfractionColor)
  }

  def logInfraction(infraction: Infraction) = println(s"Infraction detected: $infraction")

  def logInfractionExempt(infraction: Infraction) =
    println(s"Device exempt for $infractionExemptionPeriod seconds from infraction: $infraction")
}
