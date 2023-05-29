import ContainerClasses._
import Server.db
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
import org.mongodb.scala._
import org.mongodb.scala.connection._
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.Macros._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}

object Server {



  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "deliverSimulator")
  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  private val config = ConfigSource.default.loadOrThrow[Config]
  implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, KebabCase))

  val codecRegistry = fromRegistries(fromProviders(classOf[TruckModel], classOf[PacketModel], classOf[OrderModel], classOf[Plan], classOf[Location], classOf[Coordinates], classOf[TruckSetupIds], classOf[ItemDemand], classOf[RouteStepIds]), DEFAULT_CODEC_REGISTRY)
  val client: MongoClient = MongoClient("mongodb://mongo:27017")
  val db: MongoDatabase = client.getDatabase("simulator").withCodecRegistry(codecRegistry)
  val trucksCollection: MongoCollection[TruckModel] = db.getCollection("trucks")
  val locationsCollection: MongoCollection[Location] = db.getCollection("locations")

  private val fleet: Map[String,TruckModel] = Await.result(trucksCollection.find().toFuture(), 10.seconds).map(truck => truck.id -> truck).toMap
  val locations: Map[String,Location] = Await.result(locationsCollection.find().toFuture(), 10.seconds).map(loc => loc._id.toString -> loc).toMap
  val depot = locations(config.simulation.depotId)
  def main(args: Array[String]): Unit = {

    val route = {
      concat(
      path(config.servers.simulator.pathStartSimulation) {
        post {
          entity(as[PlanNoId]) { plan =>
              val itemMap = plan.items.map(i => i.itemId -> Packet(i.itemId, locations(i.locationId))).toMap
              val trucks: List[TruckModel] = plan.trucks.map(t => fleet(t.truck_id))
              val items: List[List[Packet]] = plan.trucks.map(t => t.items.map(itemMap)).toList

              val uid = DeliverSimulator.createSimulation(trucks.zip(items).toList, depot) //locations(body.depotId))
            val planWithuid = Plan(plan.trucks, plan.items, uid)
            //save plan in db non blocking

            val plansCollection: MongoCollection[Plan] = db.getCollection("plans")
            onSuccess{
              plansCollection.insertOne(planWithuid).toFuture()
            } { _ =>
              complete(HttpEntity(ContentTypes.`application/json`, planWithuid.toJson.compactPrint))
            }
            }

          }
      },
        path(config.servers.simulator.pathCreateRandomPlan) {
          get {
            val usedLocations = depot :: RandomGenerator.sample(locations.values, RandomGenerator.random.between(30, 40)).toList
            val packets = (1 until usedLocations.length).flatMap(i => (0 until RandomGenerator.random.between(1, 5)).map(_ => PacketModel("", i))).zipWithIndex.map { case (p, i) => p.copy(id = i.toString) }.toList
            createPlan(usedLocations, packets)
          }
        },
        path(config.servers.simulator.pathCreatePlan) {
          post {
            entity(as[List[ItemDemand]]) { demand =>
              val usedLocations = depot :: demand.map(d => locations(d.locationId)).distinct
              val packets = demand.map(d => PacketModel(d.itemId, usedLocations.indexOf(locations(d.locationId))))
              createPlan(usedLocations, packets)
            }
          }
        }
      )
    }

    // start server

    val bindingFuture = Http().newServerAt(config.servers.simulator.host, config.servers.simulator.port).bind(route)
      .map(serverBinding => println(s"Server started at ${serverBinding.localAddress}"))
      .recover { case ex => println(s"Server could not start!", ex) }




  }

  private def createPlan(usedLocations: List[Location], packets: List[PacketModel]) = {
    val order = OrderModel(packets, fleet.values.toList, 0, usedLocations)
    val routes = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = s"http://${config.servers.route_optimizer.host}:${config.servers.route_optimizer.port}/${config.servers.route_optimizer.path}",
      entity = HttpEntity(ContentTypes.`application/json`, order.toJson.toString())
    )).flatMap { response =>
      //TODO: handle no available routes
      response.entity.toStrict(3.seconds).map(_.data).map(_.utf8String).map(_.parseJson.convertTo[List[TruckSetup]])
    }
    onSuccess(routes) { r =>
      //TruckSetup to TruckSetupIds
      val trucksIds = r.map { ts => {
        val truckId = ts.truck_id
        val route = ts.route.map(rstep => RouteStepIds(origin = usedLocations(rstep.origin)._id.toString, destination = usedLocations(rstep.destination)._id.toString, duration = rstep.duration))
        val items = ts.items.map(item => item.toString)
        TruckSetupIds(items, route, truckId)
      }
      }.filter(_.items.nonEmpty)
      val items = packets.map(p => ItemDemand( usedLocations(p.destinationId)._id.toString, p.id))
      val planNoId = PlanNoId(trucksIds, items)
      complete(HttpEntity(ContentTypes.`application/json`, planNoId.toJson.compactPrint))
    }
  }


}

