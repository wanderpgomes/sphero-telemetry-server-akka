package ca.wglabs.telemetry.services

import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import ca.wglabs.telemetry.model.{DeviceMeasurement, Measurement, Position, Velocity}
import org.scalatest.{FunSuite, Matchers}

class DeviceTelemetryServiceSpec extends FunSuite with Matchers with ScalatestRouteTest {

    val deviceTelemetryService = new DeviceTelemetryService

    test("should be able to connect to SpheroTelemetryService websocket") {
      val wsClient = WSProbe()
      WS("/sphero-data/bb8", wsClient.flow) ~> deviceTelemetryService.route ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade shouldEqual true
        }
    }

    test("should send command yellow back if velocity is greater than the maximum of 900") {
      val wsClient = WSProbe()
      val sensorData = "{" +
        "\"velocity\": { \"unit\": \"mm/s\", \"v\": 0.0, \"vx\": 100, \"vy\": 900 }, " +
        "\"position\": { \"unit\": \"cm\", \"x\": 10, \"y\": 15 } " +
        "}"

      WS("/sphero-data/bb8", wsClient.flow) ~> deviceTelemetryService.route ~>
      check {
        wsClient.sendMessage(sensorData)
        wsClient.expectMessage("{\"color\":\"yellow\"}")

      }
    }

  test("should send command yellow back if velocity is greater than the maximum of 900 twice within 5 seconds") {
    val wsClient = WSProbe()
    val sensorData = "{" +
      "\"velocity\": { \"unit\": \"mm/s\", \"v\": 0.0, \"vx\": 100, \"vy\": 900 }, " +
      "\"position\": { \"unit\": \"cm\", \"x\": 10, \"y\": 15 } " +
      "}"

    WS("/sphero-data/bb8", wsClient.flow) ~> deviceTelemetryService.route ~>
      check {
        wsClient.sendMessage(sensorData)
        wsClient.expectMessage("{\"color\":\"yellow\"}")
      }
  }

  test("Wrong Way infraction should be detected if last measurement is closer to origin") {
    val velocity = Velocity("mm/s", 900, 900)
    var measurements = (1 to 3).map(n => DeviceMeasurement("device", Measurement(velocity, Position("cm", 0 + n, 0))))
    val measurementWithInfraction = DeviceMeasurement("device", Measurement(velocity, Position("cm", 2, 0)))
    measurements = measurements :+ measurementWithInfraction

    val result = deviceTelemetryService.isWrongWayInfraction(measurements)

    assert(result)
  }

  test("Wrong Way infraction not should be detected if last measurement is further from origin") {
    val velocity = Velocity("mm/s", 900, 900)
    val measurements = (1 to 3).map(n => DeviceMeasurement("device",Measurement(velocity, Position("cm", 0 + n, 0))))

    val result = deviceTelemetryService.isWrongWayInfraction(measurements)

    assert(!result)
  }

  test("Wrong Way infraction not should be detected if last measurements have same position") {
    val velocity = Velocity("mm/s", 900, 900)
    val measurements = (1 to 3).map(_ => DeviceMeasurement("device", Measurement(velocity, Position("cm", 0, 0))))

    val result = deviceTelemetryService.isWrongWayInfraction(measurements)

    assert(!result)
  }

  test("Wrong Way infraction not should be detected if there is only one measurement") {
    val velocity = Velocity("mm/s", 900, 900)
    val measurements = List(DeviceMeasurement("device", Measurement(velocity, Position("cm", 0 , 0))))

    val result = deviceTelemetryService.isWrongWayInfraction(measurements)

    assert(!result)
  }
}

