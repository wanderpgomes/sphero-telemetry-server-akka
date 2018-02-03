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

    test("should register device") {
      val wsClient = WSProbe()
      WS("/sphero-data/bb8", wsClient.flow) ~> spheroTelemetryService.route ~>
        check {
          wsClient.expectMessage("bb8 registered")
        }
    }

    test("should echo the message sent after registration") {
      val wsClient = WSProbe()
      WS("/sphero-data/bb8", wsClient.flow) ~> spheroTelemetryService.route ~>
        check {
          wsClient.expectMessage("bb8 registered")
          wsClient.sendMessage("{\"event\":\"dataStreaming\"}")
          wsClient.expectMessage("{\"event\":\"dataStreaming\"}")
        }
    }

}
