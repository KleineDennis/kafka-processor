package com.autoscout24.listingimages.kafka

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

case class KafkaConfig(applicationId: String, clientId: String, bootstrapServers: String, commitInternal: Duration, stateDir: String)
case class ListingImagesClientConfig(endpoint: String, id: String, secret: String, authorizationServer: String)
case class Config(kafkaConfig: KafkaConfig, listingImagesClientConfig: ListingImagesClientConfig)

/**
  * Loads the configuration with typesafe config and converts it into a Config object.
  */
object Configuration {
  def load(): Config = {
    val c = ConfigFactory.load()

    val kafkaConfig = KafkaConfig(
      c.getString("kafka.application-id"),
      c.getString("kafka.client-id"),
      c.getString("kafka.bootstrap-servers"),
      1.seconds,
      c.getString("kafka.state-dir")
    )

    val listingImagesClientConfig = ListingImagesClientConfig(
      c.getString("listing-images.endpoint"),
      c.getString("listing-images.client-id"),
      c.getString("listing-images.client-secret"),
      c.getString("listing-images.authorization-server")
    )
    Config(kafkaConfig, listingImagesClientConfig)
  }
}
