package ca.wglabs.telemetry.model



case class DeviceMeasurement(name: String, measurement: Measurement)
case class Measurement(velocity: Velocity, position: Position)
case class Velocity(unit: String, vx: Int, vy: Int, var v: Double = 0.0)
case class Position(unit: String, x: Int, y: Int)
