import ContainerClasses._
import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.Http
import spray.json.DefaultJsonProtocol._

import scala.collection.mutable
import scala.concurrent.duration._
import spray.json._

import scala.concurrent.ExecutionContextExecutor
import scala.util._
import pureconfig._
import pureconfig.generic.auto._
object Truck {

  private val config: Config = ConfigSource.default.loadOrThrow[Config]
  private val simulationSpeed: Double = config.simulation.simulationSpeed
  def apply(id:String, depot: Location, start:Long, kafkaPublisher: ActorRef[KafkaPublisher.KafkaEvent], capacity:Int): Behavior[LogisticEvent] =
    Behaviors.setup(context => new Truck(context, id, depot, start, kafkaPublisher, capacity))

  sealed trait LogisticEvent
  final case class InitTruck() extends LogisticEvent
  private val initTruckMessage = "Truck initialized"
  final case class ReceivePacketsTruck(packetList: Iterable[Packet]) extends LogisticEvent
  private val receivePacketsTruckMessage = "Truck received packets"
  final case class Departure(origin: Location, destination: Location) extends LogisticEvent
  private val departureMessage = "Truck departed"
  private val departureToDepotMessage = "Truck departed to depot"
  final case class Arrival() extends LogisticEvent
  private val arrivalMessage = "Truck arrived"
  final case class StartDeliver() extends LogisticEvent
  private val startDeliverMessage = "Truck started delivering"
  final case class EndDeliver() extends LogisticEvent
  private val endDeliverMessage = "Truck ended delivering"
  final case class EndRoute() extends LogisticEvent
  private val endRouteMessage = "Truck ended route"

  final case class TrackPosition(routeStart:Long, routePoints:List[(Coordinates,Double)]) extends LogisticEvent


  implicit val coordinatesFormat: RootJsonFormat[Coordinates] = jsonFormat2(Coordinates)
  implicit val OSRMStepGeometryFormat: RootJsonFormat[OSRMStepGeometry] = jsonFormat1(OSRMStepGeometry)
  implicit val OSRMStepFormat: RootJsonFormat[OSRMStep] = jsonFormat4(OSRMStep)
  implicit val OSRMLegFormat: RootJsonFormat[OSRMLeg] = jsonFormat1(OSRMLeg)
  implicit val OSRMRouteFormat: RootJsonFormat[OSRMRoute] = jsonFormat5(OSRMRoute)
  implicit val osrmResponseFormat: RootJsonFormat[OSRMResponse] = jsonFormat1(OSRMResponse)
}

class Truck(context: ActorContext[Truck.LogisticEvent], id: String, depot: Location, simulationStart:Long, kafkaPublisher: ActorRef[KafkaPublisher.KafkaEvent], capacity:Int) extends AbstractBehavior[Truck.LogisticEvent](context) {

  import Truck._

  implicit val system: ActorSystem[Nothing] = context.system
  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  private val cargo: mutable.Queue[Packet] = mutable.Queue[Packet]()

  private var scheduledMessage: Cancellable = _

  def getCargoMultiplier(d: Double):Double = {
    d match {
      case d if d < 0.2 => 1
      case _ => 1 + d/10
    }
  }

  def getDistanceMultiplier(distance: Double):Double =  1.05

  def getTruckTypeMultiplier(id: String):Double = 1+ id.charAt(0).toString.toDouble/50


  override def onMessage(msg: LogisticEvent): Behavior[LogisticEvent] = {
    msg match {
      case InitTruck() =>
        recordEvent(initTruckMessage)
        context.self ! Departure(depot, cargo.head.destination)
        this

      case ReceivePacketsTruck(packetList) =>
        cargo.enqueueAll(packetList)
        recordEvent(receivePacketsTruckMessage,s"packets ${packetList}")
        this

      case Departure(origin, destination) =>
        Http().singleRequest(HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://${config.servers.osrm.host}:${config.servers.osrm.port}/${config.servers.osrm.path}/${origin.coordinates};${destination.coordinates}?steps=true&geometries=geojson")).flatMap { response =>
          response.entity.toStrict(10.seconds).map(_.data).map(_.utf8String)
        }.onComplete {
          case Success(value) =>
            val route = value.parseJson.convertTo[OSRMResponse].routes.head
            val multiplier: Double = (getCargoMultiplier(cargo.size.toDouble / capacity.toDouble)) * (this.getDistanceMultiplier(route.distance)) * getTruckTypeMultiplier(id)

            val duration = route.duration * multiplier
             val routePositions: List[(Coordinates,Double)] = (route.legs.head.steps.head.geometry.coordinates.map { case Array(lon, lat) => Coordinates(lat, lon) }.head,0.0) :: route.legs.flatMap(leg => leg.steps.flatMap { step =>
              val duration = step.duration * multiplier
              val positions: List[Coordinates] = step.geometry.coordinates.tail.map { case Array(lon, lat) => Coordinates(lat, lon) }
              positions.map(p => (p,duration / positions.size))
            }).scanLeft((Coordinates(0,0),0.0))((a,b) => (b._1,a._2 + b._2)).tail.dropRight(1)

            if (destination==depot){
              recordEvent(departureToDepotMessage, s"${depot}")
            } else {
              recordEvent(departureMessage, s"${origin} to ${destination}")

            }

            scheduleEvent(Arrival(), duration)
            val routeStart = System.currentTimeMillis()
            scheduledMessage = system.scheduler.scheduleWithFixedDelay(0.seconds, (config.simulation.traceDelay / config.simulation.simulationSpeed).seconds)(()=>context.self ! TrackPosition(routeStart,routePositions))

        }
        this

      case Arrival() =>
        if (!scheduledMessage.isCancelled){
          scheduledMessage.cancel()
        }
        if (cargo.isEmpty) {
          context.self ! EndRoute()
        }
        else {
          recordEvent(arrivalMessage, s"${cargo.head.destination}")
          context.self ! StartDeliver()
        }

        this

      case StartDeliver() =>
        recordEvent(startDeliverMessage, s"Packet ${cargo.head.id} to ${cargo.head.destination}")
        // TODO: calculate deliver time
        scheduleEvent(EndDeliver(), RandomGenerator.random.between(1,3)*60)
        this


      case EndDeliver() =>
        recordEvent(endDeliverMessage, s"Packet ${cargo.head.id} to ${cargo.head.destination}")
        val currentLocation = cargo.dequeue().destination
        if (cargo.isEmpty) {
          context.self ! Departure(currentLocation, depot)
        } else if (cargo.head.destination == currentLocation) {
          context.self ! StartDeliver()
        } else {
          context.self ! Departure(currentLocation, cargo.head.destination)
        }
        this

      case EndRoute() =>
        if (!scheduledMessage.isCancelled) {
          scheduledMessage.cancel()
        }
        recordEvent(endRouteMessage)
        Behaviors.stopped

      case TrackPosition(routeStart, routePoints ) =>
        val currentTime = System.currentTimeMillis()
        val elapsedTime = (currentTime - routeStart)*config.simulation.simulationSpeed/1000.0
        //interpolate position

        val currentPoint = routePoints.find(_._2 >= elapsedTime).getOrElse(routePoints.last)
        val previousPoint = routePoints.find(_._2 < elapsedTime).getOrElse(routePoints.head)
        val ratio = if ((currentPoint._2 - previousPoint._2).abs<=0.01) 0.0 else (elapsedTime - previousPoint._2) / (currentPoint._2 - previousPoint._2)
        val interpolatedPosition = Coordinates(previousPoint._1.latitude + (currentPoint._1.latitude - previousPoint._1.latitude) * ratio, previousPoint._1.longitude + (currentPoint._1.longitude - previousPoint._1.longitude) * ratio)

        val time: Long = (currentTime - simulationStart) * simulationSpeed.toLong
        kafkaPublisher ! KafkaPublisher.ProcessTrackingEvent(TrackingEvent(context.system.name, id.toString, interpolatedPosition,time))
        val timeString = f"${time / 3600000}%02d:${(time % 3600000) / 60000}%02d:${(time % 60000) / 1000}%02d"
        //println(s"Truck $id at ${interpolatedPosition} at $timeString")
        this

    }
  }

  private def recordEvent(event: String, description:String = ""): Unit = {
    val time: Long = (System.currentTimeMillis() - simulationStart) * simulationSpeed.toLong
    val timeString = f"${time / 3600000}%02d:${(time % 3600000) / 60000}%02d:${(time % 60000) / 1000}%02d"
    kafkaPublisher ! KafkaPublisher.ProcessEvent(LogisticEvent(context.system.name, id.toString, event, time, description))
    //println(s"[${context.system.name}][$timeString][Truck $id] $event $description")
  }

  private def scheduleEvent(event: LogisticEvent, delay: Double): Cancellable = {
    context.scheduleOnce((delay / Truck.simulationSpeed).seconds, context.self, event)
  }
}
