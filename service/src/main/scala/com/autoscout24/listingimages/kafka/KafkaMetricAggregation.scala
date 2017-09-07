package com.autoscout24.listingimages.kafka

import java.lang.management.ManagementFactory
import javax.management.{MBeanServer, ObjectInstance, ObjectName}

import com.codahale.metrics.{Gauge, MetricRegistry}

import collection.JavaConverters._
import KafkaMetricAggregation._

class KafkaMetricAggregation(metrics: MetricRegistry, mBeanServer: MBeanServer = KafkaMetricAggregation.mBeanServer) {

  def registerMetrics(): Unit = {
    registerGauge("max-lag",           FetcherPattern, "records-lag-max",   _.max)
    registerGauge("fetch-size-avg",    FetcherPattern, "fetch-size-avg",    avg)
    registerGauge("fetch-latency-avg", FetcherPattern, "fetch-latency-avg", avg)
    registerGauge("fetch-rate",        FetcherPattern, "fetch-rate",        _.sum)

    registerGauge("assigned-partitions", CoordinatorPattern, "assigned-partitions", _.sum)

    registerGauge("consumer-request-size-avg", ConsumerPattern, "request-size-avg",   avg)
    registerGauge("consumer-request-rate",     ConsumerPattern, "request-rate",       _.sum)
    registerGauge("consumer-response-rate",    ConsumerPattern, "response-rate",      _.sum)
    registerGauge("consumer-byte-rate",        ConsumerPattern, "incoming-byte-rate", _.sum)
    registerGauge("consumer-connection-count", ConsumerPattern, "connection-count",   _.sum)

    registerGauge("producer-byte-rate",              ProducerPattern, "outgoing-byte-rate",     _.sum)
    registerGauge("producer-request-rate",           ProducerPattern, "request-rate",           _.sum)
    registerGauge("producer-response-rate",          ProducerPattern, "response-rate",          _.sum)
    registerGauge("producer-connection-count",       ProducerPattern, "connection-count",       _.sum)
    registerGauge("producer-error-rate",             ProducerPattern, "record-error-rate",      _.sum)
    registerGauge("producer-retry-rate",             ProducerPattern, "record-retry-rate",      _.sum)
    registerGauge("producer-buffer-available-bytes", ProducerPattern, "buffer-available-bytes", _.min)
  }

  private def registerGauge(metricName: String, beanNamePattern: String, beanAttribute: String, aggregation: List[Double] => Double = _.sum): Unit = {
    val gauge = new Gauge[Double] {
      override def getValue = aggregation(collectValues(beanNamePattern, beanAttribute))
    }
    metrics.register(metricName, gauge)
  }

  private def collectValues(name: String, attribute: String): List[Double] = {
    def hasAttribute(mbean: ObjectInstance) =
      mBeanServer.getMBeanInfo(mbean.getObjectName)
        .getAttributes
        .exists(_.getName == attribute)

    mBeanServer.queryMBeans(new ObjectName(name), null)
      .asScala
      .to[List]
      .collect { case o if hasAttribute(o) => mBeanServer.getAttribute(o.getObjectName, attribute).asInstanceOf[Double] }

  }
}

object KafkaMetricAggregation {

  val ProducerPattern    = "kafka.producer:type=producer-metrics,*"
  val ConsumerPattern    = "kafka.consumer:type=consumer-metrics,*"
  val FetcherPattern     = "kafka.consumer:type=consumer-fetch-manager-metrics,*"
  val CoordinatorPattern = "kafka.consumer:type=consumer-coordinator-metrics,*"

  val avg: List[Double] => Double = values => if (values.isEmpty) 0.0 else values.sum / values.size

  val mBeanServer: MBeanServer = ManagementFactory.getPlatformMBeanServer

}