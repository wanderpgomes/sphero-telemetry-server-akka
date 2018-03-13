package ca.wglabs.sphero.model

import java.util.Date

import akka.actor.ActorRef


sealed trait SpheroEvent

case class SpheroLeft(name: String) extends SpheroEvent
case class SpheroCommand(color: String) extends SpheroEvent
case class SpheroJoined(name: String, actorRef: ActorRef) extends SpheroEvent
case class SpheroMeasurement(name: String, measurement: Measurement) extends SpheroEvent
case class InfractionDetected(name: String, velocity: Velocity, position: Position, date: Date) extends SpheroEvent

case class Measurement(velocity: Velocity, position: Position)
case class Velocity(unit: String, vx: Int, vy: Int, var v: Double = 0.0)
case class Position(unit: String, x: Int, y: Int)
