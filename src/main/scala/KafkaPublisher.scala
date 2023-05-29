import ContainerClasses._
import akka.Done
import akka.actor.Cancellable
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.Http
import akka.kafka.ProducerMessage.MultiResultPart
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import spray.json.DefaultJsonProtocol._

import scala.collection.mutable
import scala.concurrent.duration._
import spray.json._

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util._
import pureconfig._
import pureconfig.generic.auto._
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.scaladsl.{Producer, SendProducer}
import akka.stream.scaladsl.{Sink, Source}

object KafkaPublisher {
  private val config: Config = ConfigSource.default.loadOrThrow[Config]
  def apply(): Behavior[KafkaEvent] = Behaviors.setup(context => new KafkaPublisher(context))

  sealed trait KafkaEvent
  case class ProcessEvent(event: LogisticEvent) extends KafkaEvent
  case class ProcessTrackingEvent(event: TrackingEvent) extends KafkaEvent
  case class Kill() extends KafkaEvent


  implicit val eventFormat: RootJsonFormat[LogisticEvent] = jsonFormat5(LogisticEvent)
  implicit val coordinatesFormat: RootJsonFormat[Coordinates] = jsonFormat2(Coordinates)
  implicit val trackingEventFormat: RootJsonFormat[TrackingEvent] = jsonFormat4(TrackingEvent)

}
class KafkaPublisher(context: ActorContext[KafkaPublisher.KafkaEvent]) extends AbstractBehavior[KafkaPublisher.KafkaEvent](context) {
  import KafkaPublisher._

  implicit val system: ActorSystem[Nothing] = context.system
  implicit val executionContext: ExecutionContextExecutor = system.executionContext
  import org.apache.kafka.common.serialization.StringSerializer

  private val producerSettings =
    ProducerSettings(system.settings.config.getConfig("akka.kafka.producer"), new StringSerializer, new StringSerializer)
      .withBootstrapServers(config.kafka.bootstrapServers)

  import akka.actor.typed.scaladsl.adapter._
  private val sendProducer = SendProducer(producerSettings)(system.toClassic)


  override def onMessage(msg: KafkaEvent): Behavior[KafkaEvent] = {
    msg match {
      case ProcessEvent(event) =>
        val record = new ProducerRecord[String, String](config.kafka.topic, event.toJson.compactPrint)
        sendProducer.send(record)
        this
      case ProcessTrackingEvent(event) =>
        val record = new ProducerRecord[String, String](config.kafka.trackingTopic, event.toJson.compactPrint)
        sendProducer.send(record)
        this
      case Kill() =>
          sendProducer.close()
          Behaviors.stopped
    }
  }


}



