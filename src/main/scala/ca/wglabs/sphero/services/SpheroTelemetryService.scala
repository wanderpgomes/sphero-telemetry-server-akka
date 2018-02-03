package ca.wglabs.sphero.services

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}

class SpheroTelemetryService(implicit val actorSystem : ActorSystem, implicit  val actorMaterializer: ActorMaterializer) extends Directives {

  def route: Route = path("sphero-data" / Segment) { deviceName =>
    get {
      handleWebSocketMessages(flow(deviceName))
    }
  }

  def flow(deviceName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(){ implicit builder =>
    import GraphDSL.Implicits._

    val materialization = builder.materializedValue.map(m => TextMessage(s"$deviceName registered"))

    val messagePassingFlow = builder.add(Flow[Message].map(m => m))

    val merge = builder.add(Merge[Message](2))

    materialization ~> merge.in(0)
    merge ~> messagePassingFlow

    FlowShape(merge.in(1), messagePassingFlow.out)
  })
}
