package ca.wglabs.telemetry.model

import java.util.Date

import akka.actor.ActorRef


sealed trait DeviceEvent

case class DeviceLeft(name: String) extends DeviceEvent
case class DeviceCommand(color: String) extends DeviceEvent
case class DeviceJoined(name: String, actorRef: ActorRef) extends DeviceEvent
case class DeviceMeasurement(name: String, measurement: Measurement) extends DeviceEvent
case class VelocityInfraction(name: String, velocity: Velocity, position: Position, date: Date) extends DeviceEvent
case class WrongWayInfraction(name: String, position: Position, date: Date) extends DeviceEvent
case class InfractionExempt(name: String, exempt: Boolean) extends DeviceEvent

case class Measurement(velocity: Velocity, position: Position)
case class Velocity(unit: String, vx: Int, vy: Int, var v: Double = 0.0)
case class Position(unit: String, x: Int, y: Int)
