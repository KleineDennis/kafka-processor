package com.autoscout24.listingimages.kafka

object Main extends App {
  val factory = new Factory(Configuration.load())

  val kafkaStreams = factory.kafkaStreams
  kafkaStreams.start()
  sys.addShutdownHook(kafkaStreams.close())
}