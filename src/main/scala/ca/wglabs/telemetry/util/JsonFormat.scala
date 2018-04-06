package ca.wglabs.telemetry.util

import ca.wglabs.telemetry.model.{Measurement, Position, SendDeviceResponse, Velocity}
import spray.json.DefaultJsonProtocol

object JsonFormat extends DefaultJsonProtocol {
    implicit val velocityFormat = jsonFormat4(Velocity)
    implicit val positionFormat = jsonFormat3(Position)
    implicit val measurementFormat = jsonFormat2(Measurement)
    implicit val spheroCommandFormat = jsonFormat1(SendDeviceResponse)
}
