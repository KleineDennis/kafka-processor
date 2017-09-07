package com.autoscout24.listingimages.kafka

import com.timgroup.statsd.StatsDClient
import org.slf4j.LoggerFactory

class KStreamForeachAction(listingImagesApiClient: ListingImagesApiClient)(implicit statsd: StatsDClient) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  /**
    * Process an update. Value is (rawListing, listingImages)
    *
    * Processing is currently artificially limited by the listingImageApiClient.
    *
    * This is mainly to reduce load on the service while initially adding all listing. But throttling should always
    * be safe. There should never be a too large number of changes at once. If the processor regularly falls back,
    * the rate limit should be increased.
    */
  def process(key: String, value: (String, String)): Unit = {
    value match {
      case (null, listingImagesRecord: String) =>
        logger.debug("Delete " + key)
        val testMode = listingImagesRecord.contains("tm=t")
        listingImagesApiClient.delete(key, testMode)
      case (raw: String, null) =>
        logger.debug("Create " + key)
        // Add test mode support as soon as raw-listing supports it
        listingImagesApiClient.create(key, testMode = false)
      case (_: String, _:  String) | (null, null) | null =>
        logger.debug("No changes for " + key)
    }
    statsd.increment("listing_images_join")
  }
}
