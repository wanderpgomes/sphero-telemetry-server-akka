package ca.wglabs.sphero.services

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import ca.wglabs.sphero.actor._
import ca.wglabs.sphero.model.{SpheroCommand, _}
import ca.wglabs.sphero.util.JsonFormat._
import spray.json._

class SpheroTelemetryService(implicit val actorSystem : ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends Directives {

  def route: Route = path("sphero-data" / Segment) { deviceName =>
    get {
      handleWebSocketMessages(flow(deviceName))
    }
  }


  def flow(spheroName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(actorSource){ implicit builder => spheroActor =>
    import GraphDSL.Implicits._


    val materializer: Outlet[SpheroJoined] = builder.materializedValue.map(actorRef => SpheroJoined(spheroName, actorRef)).outlet
    val source: FlowShape[Message, SpheroMeasurement] = builder.add(messagesToSpheroEvents(spheroName))

    // Flows
    val broadcast: UniformFanOutShape[SpheroEvent, SpheroEvent] = builder.add(Broadcast[SpheroEvent](2))
    val merge: UniformFanInShape[SpheroEvent, SpheroEvent] = builder.add(Merge[SpheroEvent](2))
    val back: FlowShape[SpheroEvent, Message] = builder.add(spheroEventsToMessages)


    // Sinks
    val actorSink: Inlet[SpheroEvent] = builder.add(Sink.actorRef[SpheroEvent](spheroTelemetryActorSink, SpheroLeft(spheroName))).in
    val printSink: Inlet[Any] = builder.add(Sink.foreach(println)).in

               materializer   ~>     merge
                  broadcast   ~>     merge    ~>    actorSink
    source ~>     broadcast   ~>   printSink
    back   <~     spheroActor

    FlowShape(source.in, back.out)
  })

  val actorSource = Source.actorRef[SpheroEvent](5, OverflowStrategy.fail)

  val spheroTelemetryActorSink = actorSystem.actorOf(Props(new SpheroTelemetryActor()))

  val messagesToSpheroEvents = (spheroName: String) => Flow[Message].collect {
    case TextMessage.Strict(measurement) => SpheroMeasurement(spheroName, measurement.parseJson.convertTo[Measurement])
  }

  val spheroEventsToMessages = Flow[SpheroEvent].map {
    case n: SpheroCommand => TextMessage(n.toJson.toString)
  }

}

