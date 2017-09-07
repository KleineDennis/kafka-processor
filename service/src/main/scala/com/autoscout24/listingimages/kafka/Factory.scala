package com.autoscout24.listingimages.kafka

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.codahale.metrics.{JmxReporter, MetricRegistry}
import com.timgroup.statsd.{NonBlockingStatsDClient, StatsDClient}
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
  * Simple hard coded factory for DI.
  */
class Factory(config: Config) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  lazy implicit val actorSystem = ActorSystem()
  lazy implicit val materializer = ActorMaterializer()

  lazy implicit val statsDClient: StatsDClient = new NonBlockingStatsDClient("", "localhost", 8125)

  lazy val metrics = {
    val registry = new MetricRegistry()
    val reporter = JmxReporter.forRegistry(registry).inDomain("listing-images")
    reporter.build().start()

    registry
  }

  lazy val accessTokenProvider = new Is24AccessTokenProvider(config.listingImagesClientConfig, actorSystem, materializer)

  lazy val listingImagesApiClient = new ListingImagesApiClient(config.listingImagesClientConfig, accessTokenProvider)

  lazy val kafkaProducer = Producer.create(config.kafkaConfig)
  lazy val kafkaBuilder = KStreamFactory.builder(listingImagesApiClient)
  lazy val kafkaStreams = {
    val kstreams = new KafkaStreams(kafkaBuilder, KStreamFactory.properties(config.kafkaConfig))
    kstreams.setUncaughtExceptionHandler((t: Thread, e: Throwable) => {
      logger.error("Unhandled exception. Exit!", e)
      implicit val ec = actorSystem.dispatcher
      actorSystem.scheduler.scheduleOnce(60.seconds) {
        Runtime.getRuntime.halt(2)
      }
      System.exit(1)
    })
    kstreams.setStateListener((newState: KafkaStreams.State, oldState: KafkaStreams.State) => {
      logger.info("KStreams state changed from " + oldState + " to " + newState)
    })
    new KafkaMetricAggregation(metrics).registerMetrics()
    kstreams
  }
}
