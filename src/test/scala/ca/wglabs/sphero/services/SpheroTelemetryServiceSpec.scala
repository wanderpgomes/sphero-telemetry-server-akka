package ca.wglabs.sphero.services

import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import org.scalatest.{FunSuite, Matchers}

class SpheroTelemetryServiceSpec extends FunSuite with Matchers with ScalatestRouteTest {

    val spheroTelemetryService = new SpheroTelemetryService

    test("should be able to connect to SpheroTelemetryService websocket") {
      val wsClient = WSProbe()
      WS("/sphero-data/bb8", wsClient.flow) ~> spheroTelemetryService.route ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade shouldEqual true
        }
    }

    test("should send command back if velocity is greater than the maximum of 900") {
      val wsClient = WSProbe()
      val sensorData = "{" +
        "\"velocity\": { \"unit\": \"mm/s\", \"v\": 0.0, \"vx\": 100, \"vy\": 900 }, " +
        "\"position\": { \"unit\": \"cm\", \"x\": 10, \"y\": 15 } " +
        "}"

      WS("/sphero-data/bb8", wsClient.flow) ~> spheroTelemetryService.route ~>
      check {
        wsClient.sendMessage(sensorData)
        wsClient.expectMessage("{\"color\":\"red\"}")

      }
    }



}

