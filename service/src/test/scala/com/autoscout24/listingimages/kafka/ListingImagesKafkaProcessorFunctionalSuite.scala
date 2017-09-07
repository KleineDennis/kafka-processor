package com.autoscout24.listingimages.kafka

import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringSerializer
import org.joda.time.DateTime
import org.scalatest.FunSuite
import akka.http.scaladsl.model.HttpMethods._
import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.util.Random

/**
  * This Suite is tries to capture the functionality of the whole service.
  *
  * It mocks all external systems (Kafka, authorization service and listing-images service), but tests the
  * listing-images-kafka-processor from end-to-end.
  */
class ListingImagesKafkaProcessorFunctionalSuite extends FunSuite with EmbeddedKafka {
  implicit val actorSystem =  ActorSystem()
  implicit val materialzer =  ActorMaterializer()

  class RequestHistory {
    val requestHistory = new AtomicReference(Seq[HttpRequest]())

    @tailrec
    final def add(r: HttpRequest): Unit = {
      val before = requestHistory.get()
      if (!requestHistory.compareAndSet(before, before :+ r)) {
        add(r)
      }
    }

    def get(): Seq[HttpRequest] = requestHistory.get()
    def clear(): Unit = requestHistory.set(Seq[HttpRequest]())
  }

  class ListingImagesApiMockServer {
    val requestHistory = new RequestHistory()

    val apiHandler: HttpRequest => HttpResponse = { request =>
        requestHistory.add(request)
        HttpResponse(200, entity = "")
    }

    val binding = Await.result(Http().bindAndHandleSync(apiHandler, "localhost", 0), 10.seconds)

    def close() = Await.result(binding.unbind(), 10.seconds)
  }

  class AuthorizationMockServer {
    val apiHandler: HttpRequest => HttpResponse = {
      request =>
        HttpResponse(200, entity = """{"access_token":"secret-token","token_type":"bearer","expires_in":3599,"scope":"as24-listing-images-delete-restore","kid":"tuv_2016-02-02T15:36:41.457Z","jti":"6c87e9ff-68c7-413b-ad3c-b6a5b332b787"}""")
    }

    val binding = Await.result(Http().bindAndHandleSync(apiHandler, "localhost", 0), 10.seconds)

    def close() = Await.result(binding.unbind(), 10.seconds)
  }

  def findAvailablePort: Int = {
    val serverSocket = new ServerSocket(0)
    val port = serverSocket.getLocalPort
    serverSocket.close()
    port
  }

  implicit val embeddedKafkaConfig = EmbeddedKafkaConfig(
    findAvailablePort,
    findAvailablePort,
    Map("offsets.topic.replication.factor" -> "1"),
    Map.empty,
    Map.empty)

  implicit val stringSerializer = new StringSerializer

  def withMockServers(f: (Factory, ListingImagesApiMockServer)  => Unit) = {
    EmbeddedKafka.start()
    KafkaTestHelper.createTopics("localhost:" + embeddedKafkaConfig.zooKeeperPort)

    val listingImageServer = new ListingImagesApiMockServer
    val authorizationServer = new AuthorizationMockServer

    val config = {
      val kafkaInstance = Random.nextInt()
      val kafkaConfig = KafkaConfig(
        "test-app-" + kafkaInstance,
        "test-client" + kafkaInstance,
        "localhost:" + embeddedKafkaConfig.kafkaPort,
        10.millis,
        "/tmp/kafka-streams"
      )

      val listingImagesClientConfig = ListingImagesClientConfig(
        "http://localhost:" + listingImageServer.binding.localAddress.getPort,
        "client", "secret",
        "http://localhost:" + authorizationServer.binding.localAddress.getPort
      )
      Config(kafkaConfig, listingImagesClientConfig)
    }

    val factory = new Factory(config)

    val kafkaStreams = factory.kafkaStreams
    kafkaStreams.start()

    try {
      f(factory, listingImageServer)
    } finally {
      authorizationServer.close()
      listingImageServer.close()
      kafkaStreams.close()
      EmbeddedKafka.stop()
    }
  }

  test("Test creation and deletion of listings") {
    val RawListingsTopic = "raw-listings"
    val ListingImagesTopic = "listing-images"

    withMockServers { case (factory, listingImagesServer) =>
      def publishAndWait(topic: String, listingId: String, value: String): Unit = {
        val startTime = DateTime.now
        listingImagesServer.requestHistory.clear()

        def elapsedMs(): Long =  DateTime.now().getMillis - startTime.getMillis

        publishToKafka(topic, listingId, value)
        while(listingImagesServer.requestHistory.get().size < 1 && elapsedMs() < 10000) {
          Thread.sleep(10)
        }
      }

      // new listing is created
      publishAndWait(RawListingsTopic, "listing-1", "{}")
      assert(listingImagesServer.requestHistory.get().size === 1)
      assert(listingImagesServer.requestHistory.get().head.method === POST)
      assert(listingImagesServer.requestHistory.get().head.getUri().getPathString === "/listings/listing-1")

      // the listing images service now creates images and the listing get deleted again
      publishToKafka(ListingImagesTopic, "listing-1", "{}")
      publishAndWait(RawListingsTopic, "listing-1", null)

      assert(listingImagesServer.requestHistory.get().head.method === DELETE)
      assert(listingImagesServer.requestHistory.get().head.getUri().getPathString === "/listings/listing-1")

      // images are uploaded but no listing exists yet
      publishAndWait(ListingImagesTopic, "listing-2", "{}")
      assert(listingImagesServer.requestHistory.get().head.method === DELETE)
      assert(listingImagesServer.requestHistory.get().head.getUri().getPathString === "/listings/listing-2")
    }
  }

}
