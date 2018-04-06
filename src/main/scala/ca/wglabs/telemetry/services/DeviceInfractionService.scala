package ca.wglabs.telemetry.services

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import ca.wglabs.telemetry.actor._
import ca.wglabs.telemetry.model.{SendDeviceResponse, _}
import ca.wglabs.telemetry.util.JsonFormat._
import spray.json._
import java.lang.Math._
import java.util.Date

import scala.concurrent.duration._
import akka.NotUsed
import constants._

import scala.annotation.tailrec
import scala.util.Try

/**
                                                                                              Outlet[DeviceJoined]
                                                                                            ╔══════════════════════╗
                                                                                            ║                      ║
                                                                                            ║                      ╠──┐
                                                                                            ║     Materializer     │  │───┐
                                                                                            ║                      ╠──┘   │
                                                                                            ║                      ║      │
                                                                                            ╚══════════════════════╝      │              UniformFanInShape[DeviceEvent, DeviceEvent]
                                                                                                                          │                ╔══════════════════════╗
                                                                                     FlowShape[DeviceMeasurement, VelocityInfraction]   ┌──╣                      ║                Inlet[DeviceEvent]
                                                                                            ╔══════════════════════╗      └────────────▶│  │                      ║             ╔══════════════════════╗
                                                                                            ║                      ║                    └──╣                      ║             ║                      ║
                                                                                         ┌──╣                      ╠──┐                 ┌──╣                      ╠──┐       ┌──╣      Infraction      ║
                                                                                 ┌──────▶│  │      Velocity        │  │────────────────▶│  │        Merge         │  │──────▶│  │      ActorSink       ║
                                                                                 │       └──╣                      ╠──┘                 └──╣                      ╠──┘       └──╣                      ║
                                                    UniformFanOutShape           │          ║                      ║                    ┌──╣                      ║             ║                      ║
                                          [DeviceMeasurement, DeviceMeasurement] │          ╚══════════════════════╝     ┌─────────────▶│  │                      ║             ╚══════════════════════╝
                                                    ╔══════════════════════╗     │                                       │              └──╣                      ║                          |
     FlowShape[Message, DeviceMeasurement]          ║                      ╠──┐  │   FlowShape[DeviceMeasurement, WrongWayInfraction]      ╚══════════════════════╝                          |
            ╔══════════════════════╗                ║                      │  │──┘          ╔══════════════════════╗     │                                                                   |
            ║                      ║                ║                      ╠──┘             ║                      ║     │                                                                   |
         ┌──╣                      ╠──┐          ┌──╣                      ╠──┐          ┌──╣                      ╠──┐  │                                                                   |
   ─────▶│  │       Source         │  │─────────▶│  │       Broadcast      │  │─────────▶│  │       WrongWay       │  │──┘                                                                   |
         └──╣                      ╠──┘          └──╣                      ╠──┘          └──╣                      ╠──┘                                                                      |
            ║                      ║                ║                      ╠──┐             ║                      ║                                                                         |
            ╚══════════════════════╝                ║                      │  │──┐          ╚══════════════════════╝                                                                         |
                                                    ║                      ╠──┘  │                                                                                                           |
                                                    ╚══════════════════════╝     │                Inlet[Any]                                                                                 |
                                                                                 │          ╔══════════════════════╗                                                                         |
                                                                                 │          ║                      ║                                                                         |
                                                                                 │       ┌──╣                      ║                                                                         |
                                                                                 └──────▶│  │     Print Sink       ║                                                                         |
                                                                                         └──╣                      ║                                                                         |
                                                                                            ║                      ║                                                                         |
                                                                                            ╚══════════════════════╝                                                                         |
     FlowShape[DeviceEvent, TextMessage]       Source[DeviceEvent, ActorRef]                                                                                                                 |
            ╔══════════════════════╗               ╔══════════════════════╗                                                                                                                  |
            ║                      ║               ║                      ║                                                                                                                  |
         ┌──╣                      ╠──┐         ┌──╣                      ║                                                                                                                  |
   ◀─────│  │        Back          │  │◀────────│  │     SourceActor      ║◀─ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
         └──╣                      ╠──┘         └──╣                      ║
            ║                      ║               ║                      ║
            ╚══════════════════════╝               ╚══════════════════════╝

*/

class DeviceInfractionService(implicit val actorSystem : ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends Directives {

  def route: Route = path("measurements" / Segment) { deviceName =>
    get {
      handleWebSocketMessages(flow(deviceName))
    }
  }

  def flow(deviceName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(measurementActorSource){ implicit builder => deviceActor =>
    import GraphDSL.Implicits._

    // Sources
    val materializer = builder.materializedValue.map(actorRef => AddDevice(deviceName, actorRef)).outlet
    val source = builder.add(messageToDeviceMeasurementFlow(deviceName))

    // Flows
    val merge = builder.add(Merge[InfractionManagementCmd](3))
    val back = builder.add(deviceCommandToMessageFlow)
    val velocity = builder.add(detectVelocityInfractionFlow)
    val wrongWay = builder.add(detectWrongWayInfractionFlow)
    val broadcast = builder.add(Broadcast[DeviceMeasurement](3))

    // Sinks
    val printSink = builder.add(Sink.foreach(println)).in
    val actorSink = builder.add(Sink.actorRef[InfractionManagementCmd](infractionActorSink, RemoveDevice(deviceName))).in

                                   materializer    ~>   merge
                  broadcast   ~>     velocity      ~>   merge    ~>   actorSink
    source   ~>   broadcast   ~>     wrongWay      ~>   merge
                  broadcast   ~>     printSink

    back     <~   deviceActor

    FlowShape(source.in, back.out)
  })
  // @formatter:on

  val infractionActorSink: ActorRef = actorSystem.actorOf(Props(new InfractionActor()))
  val measurementActorSource: Source[InfractionManagementCmd, ActorRef] = Source.actorRef[InfractionManagementCmd](5, OverflowStrategy.fail)

  def messageToDeviceMeasurementFlow(deviceName: String): Flow[Message, DeviceMeasurement, NotUsed] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(measurement) =>
          DeviceMeasurement(deviceName, measurement.parseJson.convertTo[Measurement])
      }

  def deviceCommandToMessageFlow: Flow[InfractionManagementCmd, TextMessage.Strict, NotUsed] =
    Flow[InfractionManagementCmd]
      .map {
        case dc: SendDeviceResponse => TextMessage(dc.toJson.toString)
      }

  def detectVelocityInfractionFlow: Flow[DeviceMeasurement, RegisterVelocityInfraction, NotUsed] =
    Flow[DeviceMeasurement]
      .map(calculateVelocity)
      .filter(isVelocityInfraction)
      .map(m => RegisterVelocityInfraction(m.name, m.measurement.velocity, m.measurement.position, new Date()))

  def detectWrongWayInfractionFlow: Flow[DeviceMeasurement, RegisterWrongWayInfraction, NotUsed] =
    Flow[DeviceMeasurement]
      .groupedWithin(10, 2 seconds)
      .filter(isWrongWayInfraction)
      .map(ms => RegisterWrongWayInfraction(ms.head.name, ms.head.measurement.position, new Date()))



  def isVelocityInfraction(m: DeviceMeasurement) = {
    m.measurement.velocity.v > velocityLimit
  }

  def isWrongWayInfraction(measurements: Seq[DeviceMeasurement]) = {
    calculateDistanceDifference(measurements.toList, 0.0) < 0.0
  }

  def calculateVelocity(m: DeviceMeasurement): DeviceMeasurement = {
    val velocity = Try(sqrt(pow(m.measurement.velocity.vx, 2) + pow(m.measurement.velocity.vy, 2))).getOrElse(0.0)
    m.measurement.velocity.v = velocity; m
  }

  @tailrec
  private def calculateDistanceDifference(measurements: List[DeviceMeasurement], acc: Double): Double = measurements match {
    case Nil => acc
    case (_ :: Nil) => acc
    case (m :: ms) =>
      if (acc >= 0.0) calculateDistanceDifference(ms.tail, distanceToOrigin(ms.head) - distanceToOrigin(m))
      else acc
  }

  private def distanceToOrigin(dm: DeviceMeasurement) = {
    Try(sqrt(pow(dm.measurement.position.x, 2) + pow(dm.measurement.position.y, 2))).getOrElse(0.0)
  }
}

