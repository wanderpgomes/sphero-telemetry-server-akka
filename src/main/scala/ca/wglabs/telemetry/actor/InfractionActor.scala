package ca.wglabs.telemetry.actor

import akka.actor.{ActorLogging, ActorRef, ActorSystem}
import akka.persistence.PersistentActor
import ca.wglabs.telemetry.model._
import ca.wglabs.telemetry.services.constants.{firstInfractionColor, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import collection.mutable.LinkedHashMap

case class Device(name: String, var points: Int = 0, var isExempt: Boolean = false)
case class DeviceWithActor(device: Device, actor: ActorRef)

case class State(devices: LinkedHashMap[String, DeviceWithActor])


case class DeviceAdded()

class InfractionActor extends PersistentActor with ActorLogging {

  val system = ActorSystem("akka-system")

  override def persistenceId: String = "telemetry-infraction-actor-persistence-id"

  var state = State(devices = LinkedHashMap[String, DeviceWithActor]())


  override def receiveRecover: Receive = {
    case event: InfractionManagementCmd =>
      println(s"Infraction actor received ${event} on recovering mood")
      updateState(event)
  }

  override def receiveCommand: Receive = {

    case event: AddDevice => {
      updateState(event)
      println(s"Device joined: ${event.name}")
    }

    case event: RemoveDevice => {
      updateState(event)
      println(s"Device left: ${event.name}")
    }

    case event: RegisterVelocityInfraction => {
      val device = state.devices(event.name).device
      if (device.isExempt) {
        println(s"Velocity Infraction detected for device name=${event.name}," +
          s" but it is exempt for $infractionExemptionPeriod seconds")
      } else {
        sendDeviceCommand(device)
        updateState(event)

        system.scheduler.scheduleOnce(infractionExemptionPeriod seconds, self, ApplyInfractionExemption(device.name, false))

        println(s"Velocity Infraction detected for device" +
          s" name=${event.name}," +
          s" velocity=${event.velocity.v} ${event.velocity.unit}," +
          s" position=(${event.position.x}, ${event.position.y}," +
          s" date=${event.date}")
      }
    }

    case event: ApplyInfractionExemption =>
      updateState(event)

    case _ => println("Invalid message")
  }





  def updateState(event: InfractionManagementCmd) = event match {
    case AddDevice(deviceName, actor) =>
      state.devices += (deviceName -> DeviceWithActor(Device(deviceName), actor))

    case RemoveDevice(deviceName) =>
      state.devices -= deviceName

    case RegisterVelocityInfraction(deviceName, _, _, _) =>
      val device = state.devices(deviceName).device
      device.isExempt = true
      device.points += 1

    case ApplyInfractionExemption(deviceName, exempt) =>
      val device = state.devices(deviceName).device
      device.isExempt = exempt
  }

  def isFirstInfraction(device: Device) = device.points equals 0


  def sendDeviceCommand(device: Device) = {
    state.devices(device.name).actor !
      SendDeviceResponse(if (isFirstInfraction(device)) firstInfractionColor else repeatedInfractionColor)
  }

}
