package ca.wglabs.sphero.model

import akka.actor.ActorRef


sealed trait SpheroEvent

case class SpheroLeft(name: String) extends SpheroEvent
case class SpheroCommand(color: String) extends SpheroEvent
case class SpheroJoined(name: String, actorRef: ActorRef) extends SpheroEvent
case class SpheroMeasurement(name: String, measurement: Measurement) extends SpheroEvent

case class Measurement(velocityX: Velocity,
                       velocityY: Velocity,
                       positionX: Position,
                       positionY: Position)

case class Velocity(unit: String, value: Int)
case class Position(unit: String, value: Int)
