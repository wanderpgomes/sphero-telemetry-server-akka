package ca.wglabs.telemetry.services

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import ca.wglabs.telemetry.actor._
import ca.wglabs.telemetry.model.{DeviceCommand, _}
import ca.wglabs.telemetry.util.JsonFormat._
import spray.json._
import java.lang.Math._
import java.util.Date

import scala.concurrent.duration._
import akka.NotUsed
import constants._

import scala.annotation.tailrec
import scala.util.Try

class DeviceTelemetryService(implicit val actorSystem : ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends Directives {

  def route: Route = path("sphero-data" / Segment) { deviceName =>
    get {
      handleWebSocketMessages(flow(deviceName))
    }
  }


  def flow(deviceName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(actorSource){ implicit builder => deviceActor =>
    import GraphDSL.Implicits._

    // Sources
    val materializer: Outlet[DeviceJoined] = builder.materializedValue.map(actorRef => DeviceJoined(deviceName, actorRef)).outlet
    val source: FlowShape[Message, DeviceMeasurement] = builder.add(messageToDeviceEventFlow(deviceName))

    // Flows
    val broadcast: UniformFanOutShape[DeviceMeasurement, DeviceMeasurement] = builder.add(Broadcast[DeviceMeasurement](3))
    val merge: UniformFanInShape[DeviceEvent, DeviceEvent] = builder.add(Merge[DeviceEvent](3))
    val back: FlowShape[DeviceEvent, Message] = builder.add(deviceCommandToMessage)
    val velocity: FlowShape[DeviceMeasurement, VelocityInfraction] = builder.add(detectVelocityInfractionFlow)
    val wrongWay: FlowShape[DeviceMeasurement, WrongWayInfraction] = builder.add(detectWrongWayInfractionFlow)

    // Sinks
    val actorSink: Inlet[DeviceEvent] = builder.add(Sink.actorRef[DeviceEvent](infractionActorSink, DeviceLeft(deviceName))).in
    val printSink: Inlet[Any] = builder.add(Sink.foreach(println)).in

                                   materializer    ~>   merge
                  broadcast   ~>     velocity      ~>   merge    ~>   actorSink
    source   ~>   broadcast   ~>     wrongWay      ~>   merge
                  broadcast   ~>     printSink

    back     <~   deviceActor

    FlowShape(source.in, back.out)
  })

  val actorSource = Source.actorRef[DeviceEvent](5, OverflowStrategy.fail)

  val infractionActorSink = actorSystem.actorOf(Props(new InfractionActor()))

  def messageToDeviceEventFlow(deviceName: String): Flow[Message, DeviceMeasurement, NotUsed] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(measurement) =>
          DeviceMeasurement(deviceName, measurement.parseJson.convertTo[Measurement])
      }

  val deviceCommandToMessage = Flow[DeviceEvent].map {
    case dc: DeviceCommand => TextMessage(dc.toJson.toString)
  }

  val detectVelocityInfractionFlow: Flow[DeviceMeasurement, VelocityInfraction, NotUsed] =
    Flow[DeviceMeasurement]
      .map(calculateVelocity)
      .filter(isVelocityInfraction)
      .map(m => VelocityInfraction(m.name, m.measurement.velocity, m.measurement.position, new Date()))

  val detectWrongWayInfractionFlow: Flow[DeviceMeasurement, WrongWayInfraction, NotUsed] =
    Flow[DeviceMeasurement]
      .groupedWithin(10, 2 seconds)
      .filter(isWrongWayInfraction)
      .map(ms => WrongWayInfraction(ms.head.name, ms.head.measurement.position, new Date()))

  def calculateVelocity(m: DeviceMeasurement): DeviceMeasurement = {
    val velocity = Try(sqrt(pow(m.measurement.velocity.vx, 2) + pow(m.measurement.velocity.vy, 2))).getOrElse(0.0)
    m.measurement.velocity.v = velocity; m
  }

  def isVelocityInfraction(m: DeviceMeasurement) = {
    m.measurement.velocity.v > velocityLimit
  }

  def isWrongWayInfraction(measurements: Seq[DeviceMeasurement]) = {
    calculateDistanceDifference(measurements.toList, 0.0) < 0
  }

  @tailrec
  private def calculateDistanceDifference(measurements: List[DeviceMeasurement], acc: Double): Double = measurements match {
    case Nil => acc
    case (_ :: Nil) => acc
    case (m :: ms) =>
      if (acc >= 0) calculateDistanceDifference(ms.tail, distanceToOrigin(ms.head) - distanceToOrigin(m))
      else acc
  }

  private def  distanceToOrigin(dm: DeviceMeasurement) = {
    Try(sqrt(pow(dm.measurement.position.x, 2) + pow(dm.measurement.position.y, 2))).getOrElse(0.0)
  }
}

