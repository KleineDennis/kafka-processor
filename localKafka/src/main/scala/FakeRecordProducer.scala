import java.util.UUID
import org.apache.kafka.clients.producer.ProducerRecord
import play.api.libs.json.Json
import Formats._
import com.autoscout24.listingimages.kafka.model.{Image, ListingImages}
import org.joda.time.DateTime
import com.autoscout24.listingimages.kafka.{KafkaConfig, Producer}
import ListingImages._
import scala.concurrent.duration._

object FakeRecordProducer {

  val producer = Producer.create(KafkaConfig("raw-listings-producer", "raw-listings-producer", "localhost:6001", 100.millis, "/tmp/kafka-streams"))
  val maxListingId = 10
  val rnd = scala.util.Random

  val rawListingTopic = "raw-listings"
  val listingImagesTopic = "listing-images"

  def produceRawListing() = {
    val key = rnd.nextInt(maxListingId)
    val listing = RawListing(key, rnd.alphanumeric.take(8).toArray.mkString)

    val data = Json.toJson(listing).toString.getBytes("UTF-8")
    producer.send(new ProducerRecord(rawListingTopic, listing.id.toString, data))
    producer.flush()
  }

  def produceImages() = {
    val key = rnd.nextInt(maxListingId)
    val modified = DateTime.now()
    val caption = if(rnd.nextBoolean()) Some(rnd.alphanumeric.take(8).toArray.mkString) else None
    val images = List(
      Image(UUID.randomUUID().toString, "StandardImage", caption, "url-template"),
      Image(UUID.randomUUID().toString, "StandardImage", caption, "url-template")
    )
    val deletedAt = if(rnd.nextBoolean()) Some("2017-08-01 16:00:00.000Z") else None
    val testMode = rnd.nextBoolean()
    val listing = ListingImages(key.toString, dateTimeFormatter.print(modified), deletedAt, testMode, DateTime.now().getMillis, images)

    val data = Json.toJson(listing).toString.getBytes("UTF-8")
    producer.send(new ProducerRecord(listingImagesTopic, key.toString, data))
    producer.flush()
  }

  def deleteImages() = {
    producer.send(new ProducerRecord(listingImagesTopic, rnd.nextInt(maxListingId).toString, null))
    producer.flush()
  }

  def deleteRawListing() = {
    producer.send(new ProducerRecord(rawListingTopic, rnd.nextInt(maxListingId).toString, null))
    producer.flush()
  }
}