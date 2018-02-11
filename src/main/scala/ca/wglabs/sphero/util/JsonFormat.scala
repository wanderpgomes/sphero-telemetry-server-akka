package ca.wglabs.sphero.util

import ca.wglabs.sphero.model.{Measurement, Position, SpheroCommand, Velocity}
import spray.json.DefaultJsonProtocol

object JsonFormat extends DefaultJsonProtocol {
    implicit val velocityFormat = jsonFormat2(Velocity)
    implicit val positionFormat = jsonFormat2(Position)
    implicit val measurementFormat = jsonFormat4(Measurement)
    implicit val spheroCommandFormat = jsonFormat1(SpheroCommand)
}
