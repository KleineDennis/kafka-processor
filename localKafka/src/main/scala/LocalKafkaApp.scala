import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import com.autoscout24.listingimages.kafka.KafkaTestHelper

object LocalKafkaApp extends App {

  val embeddedKafkaConfig = EmbeddedKafkaConfig(
    customBrokerProperties = Map("offsets.topic.replication.factor" -> "1"))

  EmbeddedKafka.start()(embeddedKafkaConfig)
  KafkaTestHelper.createTopics("localhost:6000")
  Thread.sleep(500)
  val consumer = new DebugConsumer(KafkaTestHelper.topics)

  // port 6001
  sys.ShutdownHookThread {
    consumer.close()
    EmbeddedKafka.stop()
  }

  val rnd = scala.util.Random

  while (true) {
    rnd.nextInt(4) match {
      case 0 => FakeRecordProducer.produceRawListing()
      case 1 => FakeRecordProducer.deleteRawListing()
      case 2 => FakeRecordProducer.produceImages()
      case 3 => FakeRecordProducer.deleteImages()
    }
    Thread.sleep(500)
  }


}
