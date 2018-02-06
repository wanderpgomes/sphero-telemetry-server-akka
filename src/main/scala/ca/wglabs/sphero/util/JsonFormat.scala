package ca.wglabs.sphero.util

import ca.wglabs.sphero.actor.{Measurement, Velocity}
import ca.wglabs.sphero.model.SpheroNotified
import spray.json.DefaultJsonProtocol

object JsonFormat extends DefaultJsonProtocol {
    implicit val velocityFormat = jsonFormat2(Velocity)
    implicit val measurementFormat = jsonFormat2(Measurement)
    implicit val spheroNotificationFormat = jsonFormat1(SpheroNotified)
}
