package com.autoscout24.listingimages.kafka

import java.util.Properties

import com.timgroup.statsd.StatsDClient
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.{KStreamBuilder, KTable, ValueJoiner, ValueMapper}
import org.apache.kafka.streams.processor.{StateStoreSupplier, WallclockTimestampExtractor}
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.internals.InMemoryKeyValueStoreSupplier

object KStreamFactory {

  def properties(kafkaConfig: KafkaConfig): Properties = {
    val megabyte = 1024 * 1024
    val batchSize = (10 * megabyte).toString
    val producerBuffer = (64 * megabyte).toString
    val stringSerde = Serdes.String()

    val props = new Properties
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, kafkaConfig.applicationId)
    props.put(CommonClientConfigs.CLIENT_ID_CONFIG, kafkaConfig.clientId)
    props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.bootstrapServers)

    props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, "3")
    props.put(StreamsConfig.POLL_MS_CONFIG, "100")
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, stringSerde.getClass.getName)
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, stringSerde.getClass.getName)
    props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, "30")
    props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, classOf[WallclockTimestampExtractor].getName)
    props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, kafkaConfig.commitInternal.toMillis.toString)
    props.put(StreamsConfig.STATE_DIR_CONFIG, kafkaConfig.stateDir)

    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, batchSize)
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1000") // because of throttling this processing can take about 3s

    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "2147483647")
    props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "1000")
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
    props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, batchSize)
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, producerBuffer)
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")

    props
  }

  private def buildTable(builder: KStreamBuilder,
      sourceTopic: String,
      existsTopic: String,
      valueMapper: ValueMapper[String, String]): KTable[String, String] = {

    val inMemory: StateStoreSupplier[KeyValueStore[_,_]] = new InMemoryKeyValueStoreSupplier(
      s"$existsTopic-in-memory", Serdes.String(), Serdes.String(), false, new java.util.HashMap[String, String]())

    val existStream = builder.stream[String, String](sourceTopic)
      .mapValues(valueMapper)
      .to(existsTopic)

    builder.table[String, String](existsTopic, inMemory)
  }

  def builder(listingImagesApiClient: ListingImagesApiClient)(implicit statsd: StatsDClient): KStreamBuilder = {
    val builder = new KStreamBuilder
    val mapToEmptyString: ValueMapper[String, String] = (value: String) => if (value != null) "" else null

    val listingImages = buildTable(builder, "listing-images", "listing-images-exists", DeleteAtAndTestModeAwareValueMapper)
    val rawListings = buildTable(builder, "raw-listings", "listing-images-raw-listings-exists", mapToEmptyString)

    val action = new KStreamForeachAction(listingImagesApiClient)
    val joiner: ValueJoiner[String, String, (String, String)] = (listing, raw) => (listing, raw)
    rawListings.outerJoin[String, (String, String)](listingImages, joiner).toStream.foreach(action.process)

    builder
  }
}
