package ca.wglabs.sphero.services

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import ca.wglabs.sphero.actor._
import ca.wglabs.sphero.model.{SpheroNotified, _}
import ca.wglabs.sphero.util.JsonFormat._
import spray.json._

class SpheroTelemetryService(implicit val actorSystem : ActorSystem, implicit  val actorMaterializer: ActorMaterializer) extends Directives {

  def route: Route = path("sphero-data" / Segment) { deviceName =>
    get {
      handleWebSocketMessages(flow(deviceName))
    }
  }

  val drivingAreaActor = actorSystem.actorOf(Props(new DrivingAreaActor()))
  val spheroActorSource = Source.actorRef[SpheroEvent](5, OverflowStrategy.fail)

  def flow(spheroName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(spheroActorSource){ implicit builder => spheroActor =>
    import GraphDSL.Implicits._

    val materialization = builder.materializedValue.map(playerActorRef => SpheroJoined(spheroName, playerActorRef))
    val merge = builder.add(Merge[SpheroEvent](2))

    val messagesToSpheroEventsFlow = builder.add(Flow[Message].collect {
      case TextMessage.Strict(measurement) => {
        SpheroMeasurementReceived(spheroName, measurement.parseJson.convertTo[Measurement])
      }
    })

    val spheroEventsToMessagesFlow = builder.add(Flow[SpheroEvent].map {
      case n:SpheroNotified => TextMessage(n.toJson.toString)
    })

    val drivingAreaActorSink = Sink.actorRef[SpheroEvent](drivingAreaActor, SpheroLeft(spheroName))

    materialization ~> merge ~> drivingAreaActorSink
    messagesToSpheroEventsFlow ~> merge

    spheroActor ~> spheroEventsToMessagesFlow

    FlowShape(messagesToSpheroEventsFlow.in, spheroEventsToMessagesFlow.out)
  })
}

