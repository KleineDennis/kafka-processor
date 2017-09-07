import java.util.Properties

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.json.Json

import scala.collection.JavaConverters._

class DebugConsumer(topics: Seq[String]) {

  private val props = {
    val props = new Properties
    props.put(CommonClientConfigs.CLIENT_ID_CONFIG, "local-kafka-consumer")
    props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:6001")
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "local-kafka-consumer")
    props.put("enable.auto.commit", "false")
    props.put("session.timeout.ms", "30000")
    props.put("key.deserializer", classOf[StringDeserializer].getName)
    props.put("value.deserializer", classOf[StringDeserializer].getName)
    props.put("auto.offset.reset", "earliest")

    props
  }

  val consumer = new KafkaConsumer[String, String](props)

  consumer.subscribe(topics.asJava)

  val thread = new Thread({ () =>
      while(!Thread.currentThread().isInterrupted) {
        val records = consumer.poll(50)
        records.iterator().forEachRemaining { record  =>
          if (record.value != null) {
            println(record.topic + ": "  + record.key() + s" -> '${record.value}'")
          } else {
            println(record.topic + ": "  + record.key() + " -> deleted")
          }
        }
      }
  })

  thread.start()

  def close(): Unit = {
    thread.interrupt()
    consumer.close()
  }
}
