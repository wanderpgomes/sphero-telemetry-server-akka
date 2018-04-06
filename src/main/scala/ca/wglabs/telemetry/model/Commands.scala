package ca.wglabs.telemetry.model

import java.util.Date

import akka.actor.ActorRef

sealed trait InfractionManagementCmd

case class RemoveDevice(name: String) extends InfractionManagementCmd
case class AddDevice(name: String, actorRef: ActorRef) extends InfractionManagementCmd

case class RegisterVelocityInfraction(name: String, velocity: Velocity, position: Position, date: Date) extends InfractionManagementCmd
case class RegisterWrongWayInfraction(name: String, position: Position, date: Date) extends InfractionManagementCmd
case class ApplyInfractionExemption(name: String, exempt: Boolean) extends InfractionManagementCmd

case class SendDeviceResponse(color: String) extends InfractionManagementCmd
