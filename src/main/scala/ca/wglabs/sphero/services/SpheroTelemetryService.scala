package ca.wglabs.sphero.services

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import ca.wglabs.sphero.actor._
import ca.wglabs.sphero.model.{SpheroChangeColor, _}
import ca.wglabs.sphero.util.JsonFormat._
import spray.json._
import java.lang.Math._
import java.util.Date

import constants._

import scala.util.Try

class SpheroTelemetryService(implicit val actorSystem : ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends Directives {

  def route: Route = path("sphero-data" / Segment) { deviceName =>
    get {
      handleWebSocketMessages(flow(deviceName))
    }
  }


  def flow(spheroName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(actorSource){ implicit builder => spheroActor =>
    import GraphDSL.Implicits._


    // Sources
    val materializer: Outlet[SpheroJoined] = builder.materializedValue.map(actorRef => SpheroJoined(spheroName, actorRef)).outlet
    val source: FlowShape[Message, SpheroMeasurement] = builder.add(rawMessagesToSpheroEvents(spheroName))

    // Flows
    val broadcast: UniformFanOutShape[SpheroEvent, SpheroEvent] = builder.add(Broadcast[SpheroEvent](2))
    val merge: UniformFanInShape[SpheroEvent, SpheroEvent] = builder.add(Merge[SpheroEvent](2))
    val back: FlowShape[SpheroEvent, Message] = builder.add(spheroEventsToRawMessages)
    val calc: FlowShape[SpheroMeasurement, SpheroMeasurement] = builder.add(calculateVelocity)
    val filter: FlowShape[SpheroEvent, InfractionDetected] = builder.add(filterVelocityOverLimit(spheroName))


    // Sinks
    val actorSink: Inlet[SpheroEvent] = builder.add(Sink.actorRef[SpheroEvent](spheroTelemetryActorSink, SpheroLeft(spheroName))).in
    val printSink: Inlet[Any] = builder.add(Sink.foreach(println)).in

                                    materializer      ~>  merge
                           broadcast   ~>    filter   ~>  merge    ~>   actorSink
    source   ~>   calc ~>  broadcast   ~>   printSink

    back     <~   spheroActor

    FlowShape(source.in, back.out)
  })

  val actorSource = Source.actorRef[SpheroEvent](5, OverflowStrategy.fail)

  val spheroTelemetryActorSink = actorSystem.actorOf(Props(new SpheroTelemetryActor()))

  val rawMessagesToSpheroEvents = (spheroName: String) => Flow[Message].collect {
    case TextMessage.Strict(measurement) => SpheroMeasurement(spheroName, measurement.parseJson.convertTo[Measurement])
  }

  val spheroEventsToRawMessages = Flow[SpheroEvent].map {
    case n: SpheroChangeColor => TextMessage(n.toJson.toString)
  }

  val calculateVelocity = Flow[SpheroMeasurement].map(sm => {
      val velocity = Try(sqrt(pow(sm.measurement.velocity.vx, 2) + pow(sm.measurement.velocity.vy, 2))).getOrElse(0.0)
      sm.measurement.velocity.v = velocity
      sm
    }
  )

  val filterVelocityOverLimit = (spheroName: String) => Flow[SpheroEvent]
    .map { case sm:SpheroMeasurement => sm}
    .filter(_.measurement.velocity.v > maxVelocity)
    .map(sm => InfractionDetected(spheroName, sm.measurement.velocity, sm.measurement.position, new Date()))

}

