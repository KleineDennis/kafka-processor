package com.autoscout24.listingimages.kafka

import java.util.Properties
import kafka.admin.AdminUtils
import kafka.utils.ZkUtils

object KafkaTestHelper {
  val topics = Seq("raw-listings", "listing-images", "listing-images-exists", "listing-images-raw-listings-exists")

  def createTopics(zookeeperHost: String): Unit = {
    val zkUtils = ZkUtils.apply(zookeeperHost, 10 * 1000, 10 * 1000, isZkSecurityEnabled = false)
    val topicConfig = new Properties()
    topics.foreach { topic =>
      AdminUtils.createTopic(zkUtils, topic, 1, 1, topicConfig)
    }
  }
}
