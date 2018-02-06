package ca.wglabs.sphero.model

import akka.actor.ActorRef


sealed trait SpheroEvent

case class SpheroJoined(name: String, actorRef: ActorRef) extends SpheroEvent
case class SpheroLeft(name: String) extends SpheroEvent
case class SpheroNotification(color: String) extends SpheroEvent
case class SpheroMeasurement(name: String) extends SpheroEvent

