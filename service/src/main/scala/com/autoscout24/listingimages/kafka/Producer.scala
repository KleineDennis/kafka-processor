package com.autoscout24.listingimages.kafka

import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

object Producer {
  def create(kafkaConfig: KafkaConfig): KafkaProducer[String, Array[Byte]] = {
    val prodProps = {
      val props = new Properties
      props.put(CommonClientConfigs.CLIENT_ID_CONFIG, kafkaConfig.clientId)
      props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.bootstrapServers)
      props.put("acks", "all")
      props.put("retries", "1000")
      props.put("retry.backoff.ms", "500")
      props.put("max.in.flight.requests.per.connection", "1")
      props.put("key.serializer", classOf[StringSerializer].getName)
      props.put("value.serializer", classOf[StringSerializer].getName)
      props.put("value.serializer", classOf[ByteArraySerializer].getName)
      props
    }

     new KafkaProducer[String, Array[Byte]](prodProps)
  }
}
