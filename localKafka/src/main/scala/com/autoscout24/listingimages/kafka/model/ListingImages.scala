package com.autoscout24.listingimages.kafka.model

import org.joda.time.format.DateTimeFormat
import play.api.libs.json.Json

/**
  * Real listing images like produced by the listing-images-ddb-lamba
  */
case class ListingImages(
  listingId: String,
  lastModified: String,
  deletedAt: Option[String],
  testMode: Boolean,
  version: Long,
  images: List[Image]
)

object ListingImages {
  implicit val imageFormat = Json.format[Image]
  implicit val listingImagesFormat = Json.format[ListingImages]

  val dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZoneUTC()
}

/**
  * Real Image like produced by the listing-images-ddb-lamba
  */
case class Image(
  imageId: String,
  imageType: String,
  caption: Option[String],
  urlTemplate: String
)