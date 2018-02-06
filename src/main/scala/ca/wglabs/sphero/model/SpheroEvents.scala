package ca.wglabs.sphero.model

import akka.actor.ActorRef
import ca.wglabs.sphero.actor.Measurement


sealed trait SpheroEvent

case class SpheroJoined(name: String, actorRef: ActorRef) extends SpheroEvent
case class SpheroLeft(name: String) extends SpheroEvent
case class SpheroNotified(color: String) extends SpheroEvent
case class SpheroMeasurementReceived(name: String, measurement: Measurement) extends SpheroEvent

