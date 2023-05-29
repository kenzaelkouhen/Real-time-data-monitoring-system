import org.mongodb.scala.bson.ObjectId
import spray.json.DefaultJsonProtocol._
import spray.json.{DeserializationException, JsString, JsValue, RootJsonFormat}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.{RootJsonFormat, enrichAny}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.io.StdIn
import pureconfig._
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import org.mongodb.scala._
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.Macros._
import org.bson.codecs.configuration.CodecRegistries.{fromRegistries, fromProviders}

object ContainerClasses {

  final case class Location(_id: ObjectId, name: String, coordinates: Coordinates){
    override def toString: String = s"($name, [$coordinates])"
  }
  final case class Coordinates(latitude: Double, longitude: Double){
    override def toString: String = s"$longitude,$latitude"
  }
  final case class Packet(id:String, destination: Location) {
    override def toString: String = s"($id, $destination)"
  }

  final case class PacketModel(id:String, destinationId: Int)
  final case class TruckModel(_id: ObjectId, id: String, capacity: Int)
  final case class OrderModel(items: List[PacketModel], fleet: List[TruckModel], depotId: Int, locations: List[Location])

  final case class RouteStep(origin: Int, destination: Int, duration: Int)

  final case class RouteStepIds(origin: String, destination: String, duration: Int)

  final case class TruckSetup(items: List[String], route: List[RouteStep], truck_id: String)

  final case class TruckSetupIds(items: List[String], route: List[RouteStepIds], truck_id: String)

  final case class PlanNoId(trucks: List[TruckSetupIds], items: List[ItemDemand])
  final case class Plan(trucks: List[TruckSetupIds], items: List[ItemDemand], simulationId:String)
final case class ItemDemand(locationId: String, itemId: String)

  final case class OSRMResponse(routes: List[OSRMRoute])
  final case class OSRMRoute(distance: Double, duration: Double, weight_name: String, weight: Double, legs: List[OSRMLeg])

  final case class OSRMLeg(steps: List[OSRMStep])
  final case class OSRMStep(distance: Double, duration: Double, name: String, geometry: OSRMStepGeometry)
  final case class OSRMStepGeometry(coordinates: List[Array[Double]])




  final case class Config(simulation:SimulationConfig, servers: ServersConfig, kafka: KafkaConfig)
  final case class SimulationConfig(simulationPrefix:String, simulationSpeed:Double, traceDelay: Double, depotId:String)
  final case class ServersConfig(osrm: ServerConfig, route_optimizer: ServerConfig, simulator: SimulationServerConfig)
  final case class ServerConfig(host: String, port: Int, path:String)

  final case class SimulationServerConfig(host: String, port: Int, pathStartSimulation:String, pathCreatePlan:String, pathCreateRandomPlan:String)
  final case class KafkaConfig(bootstrapServers: String, topic: String, trackingTopic:String, groupId: String)


  final case class LogisticEvent(simulationId: String, truckId: String, eventType: String, eventTime: Long, eventDescription: String)
  final case class TrackingEvent(simulationId: String, truckId: String, coordinates: Coordinates, eventTime: Long)


  implicit val objectIdFormat: RootJsonFormat[ObjectId] = new RootJsonFormat[ObjectId] {
    def write(obj: ObjectId): JsValue = JsString(obj.toHexString)

    def read(json: JsValue): ObjectId = json match {
      case JsString(s) => new ObjectId(s)
      case _ => throw DeserializationException("ObjectId expected")
    }
  }
  implicit val coordinatesFormat: RootJsonFormat[Coordinates] = jsonFormat2(Coordinates)
  implicit val locationFormat: RootJsonFormat[Location] = jsonFormat3(Location)
  implicit val truckModelFormat: RootJsonFormat[TruckModel] = jsonFormat3(TruckModel)
  implicit val packetModelFormat: RootJsonFormat[PacketModel] = jsonFormat2(PacketModel)
  implicit val orderModelFormat: RootJsonFormat[OrderModel] = jsonFormat4(OrderModel)


  implicit val routeStepFormat: RootJsonFormat[RouteStep] = jsonFormat3(RouteStep)
  implicit val routeStepIdsFormat: RootJsonFormat[RouteStepIds] = jsonFormat3(RouteStepIds)
  implicit val truckSetupIdsFormat: RootJsonFormat[TruckSetupIds] = jsonFormat3(TruckSetupIds)
  implicit val truckSetupFormat: RootJsonFormat[TruckSetup] = jsonFormat3(TruckSetup)
  implicit val locationDemandFormat: RootJsonFormat[ItemDemand] = jsonFormat2(ItemDemand)
  implicit val planFormat: RootJsonFormat[Plan] = jsonFormat3(Plan)
  implicit val planNoIdFormat: RootJsonFormat[PlanNoId] = jsonFormat2(PlanNoId)
}
