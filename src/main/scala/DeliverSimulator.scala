
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import pureconfig.ConfigSource
import pureconfig.generic.auto._

object DeliverSimulator {
  import ContainerClasses._

  private val config = ConfigSource.default.loadOrThrow[Config]
  private def createDummySimulation(): String = {
    val uid = java.util.UUID.randomUUID.toString
    val guardian: ActorRef[SimulationGuardian.Command] = createGuardian(uid)
    guardian ! SimulationGuardian.InitDummySimulation()
    uid
  }

  def createSimulation(trucks: List[(TruckModel,List[Packet])], depot:Location): String = {
    val uid = java.util.UUID.randomUUID.toString
    val guardian: ActorRef[SimulationGuardian.Command] = createGuardian(uid)
    guardian ! SimulationGuardian.InitSimulation(trucks, depot)
    uid
  }

  private def createGuardian(uid:String) = {
    val system = ActorSystem[Truck.LogisticEvent](Behaviors.empty, s"$uid")
    val guardian = system.systemActorOf(SimulationGuardian(), s"${config.simulation.simulationPrefix}_$uid")
    guardian
  }

  def main(args: Array[String]): Unit = {
    createDummySimulation()
  }

}
