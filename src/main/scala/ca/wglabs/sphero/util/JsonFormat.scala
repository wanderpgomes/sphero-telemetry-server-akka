package ca.wglabs.sphero.util

import ca.wglabs.sphero.actor.IncomingMeasurement
import ca.wglabs.sphero.model.SpheroNotification
import spray.json.DefaultJsonProtocol

object JsonFormat extends DefaultJsonProtocol {
    implicit val incomingMessageFormat = jsonFormat1(IncomingMeasurement)
    implicit val spheroNotificationFormat = jsonFormat1(SpheroNotification)
}
