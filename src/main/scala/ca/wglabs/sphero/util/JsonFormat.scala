package ca.wglabs.sphero.util

import ca.wglabs.sphero.model.{Measurement, Position, DeviceCommand, Velocity}
import spray.json.DefaultJsonProtocol

object JsonFormat extends DefaultJsonProtocol {
    implicit val velocityFormat = jsonFormat4(Velocity)
    implicit val positionFormat = jsonFormat3(Position)
    implicit val measurementFormat = jsonFormat2(Measurement)
    implicit val spheroCommandFormat = jsonFormat1(DeviceCommand)
}
