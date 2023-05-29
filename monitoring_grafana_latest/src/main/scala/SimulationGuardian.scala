import ContainerClasses._
import Server.{locations,fleet}
import akka.actor.typed.{ActorRef, Behavior, Signal, Terminated}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

object SimulationGuardian {
  def apply(): Behavior[Command] = Behaviors.setup(context => new SimulationGuardian(context))

  sealed trait Command
  final case class InitDummySimulation() extends Command
  final case class InitSimulation(truckList: List[(TruckModel,List[Packet])], depot:Location) extends Command

}

class SimulationGuardian(context: ActorContext[SimulationGuardian.Command] ) extends AbstractBehavior[SimulationGuardian.Command](context) {

  import SimulationGuardian._
  private var truckCount:Int = 0
  var kafkaPublisher: ActorRef[KafkaPublisher.KafkaEvent] = _

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case InitDummySimulation() =>
        kafkaPublisher = context.spawn(KafkaPublisher(), "kafkaPublisher")
        val now = System.currentTimeMillis()
        val depot = locations.head._2
        val trucks = (0 to 3).map(i => context.spawn(Truck(i.toString, depot, now, kafkaPublisher,100), s"truck$i")).toList
        truckCount = trucks.size
        trucks.foreach(truck => {
          //truck ! Truck.ReceivePacketsTruck((0 to RandomGenerator.random.between(1, 3)).map(s => Packet(s.toString, locations.head)))

          context.watch(truck)
        })
        trucks.foreach(truck => {
          truck ! Truck.InitTruck()
        })
        this
      case InitSimulation(truckList, depot ) =>
        truckCount = truckList.count(_._2.nonEmpty)
        kafkaPublisher = context.spawn(KafkaPublisher(), "kafkaPublisher")
        val now = System.currentTimeMillis()
        truckList.filter(_._2.nonEmpty).foreach{ case (truck, items) =>
          val truckActor = context.spawn(Truck(truck.id, depot, now, kafkaPublisher, truck.capacity), s"truck${truck.id}")
          truckActor ! Truck.ReceivePacketsTruck(items)
          context.watch(truckActor)
          truckActor ! Truck.InitTruck()
        }
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
        case Terminated(_) =>
          truckCount -= 1
          if (truckCount == 0) {
            context.system.terminate()
            Behaviors.stopped
          } else {
            this
          }


  }
}