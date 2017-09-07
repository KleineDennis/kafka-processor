package com.autoscout24.listingimages.kafka

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.timgroup.statsd.StatsDClient
import org.mockito.Mockito._
import org.scalatest.FunSuite

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ListingImagesApiClientSuite extends FunSuite {

  def mockServer(numberOfErrors: Int) = {
    val responses = new AtomicReference[List[(Int, Boolean)]](List[(Int, Boolean)]())

    val requestHandler: HttpRequest => HttpResponse = {
      case HttpRequest(POST, Uri.Path("/listings/1"), headers, _, _) =>
        val currentRequestNumber = responses.get.size
        val authorization = headers.find(_.is("authorization")).map(_.value()).getOrElse("")
        val testMode = headers.find(_.is("x-testmode")).exists(_.value() == "true")

        authorization match {
          case _ if currentRequestNumber < numberOfErrors =>
            responses.set(responses.get() :+ (503, testMode))
            HttpResponse(503, entity = "Internal server error")
          case "Bearer token" =>
            responses.set(responses.get() :+ (200, testMode))
            HttpResponse(200, entity = "")
        }
      case HttpRequest(DELETE, Uri.Path("/listings/1"), headers, _, _) =>
        val currentRequestNumber = responses.get.size
        val authorization = headers.find(_.is("authorization")).map(_.value()).getOrElse("")
        val testMode = headers.find(_.is("x-testmode")).exists(_.value() == "true")

        authorization match {
          case _ if currentRequestNumber < numberOfErrors =>
            responses.set(responses.get() :+ (503, testMode))
            HttpResponse(503, entity = "Internal server error")
          case "Bearer token" =>
            responses.set(responses.get() :+ (200, testMode))
            val response = headers.find(_.is("x-testmode")).map(_.value()).getOrElse("")
            HttpResponse(200, entity = response)
        }
      case HttpRequest(DELETE, Uri.Path("/listings/no-such-listing"), headers, _, _) =>
        val testMode = headers.find(_.is("x-testmode")).exists(_.value() == "true")
        responses.set(responses.get() :+ (404, testMode))
        HttpResponse(404, entity = "")
    }

    (responses, requestHandler)
  }

  def getApiForServer(server: HttpRequest => HttpResponse, accessTokenProvider: AccessTokenProvider) = {
    implicit val statsd = mock(classOf[StatsDClient])


    implicit val actorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val bindingFuture = Http().bindAndHandleSync(server, "localhost", 0)
    val port = Await.result(bindingFuture, 10.seconds).localAddress.getPort
    val config = ListingImagesClientConfig(s"http://localhost:$port", "", "", "")

    new ListingImagesApiClient(config, accessTokenProvider) {
      override def backoff(i: Int): Unit = {}
    }
  }

  def getSuccessfulAccessTokenProvider() = {
    val accessTokenProvider = mock(classOf[AccessTokenProvider])
    when(accessTokenProvider.getAccessToken()).thenReturn(Future.successful("token"))
    accessTokenProvider
  }

  test("Create a listing successfully") {
    val accessTokenProvider = getSuccessfulAccessTokenProvider()
    val (responses, route) = mockServer(0)
    val api = getApiForServer(route, accessTokenProvider)

    api.create("1", testMode = false)
    api.create("1", testMode = true)

    assert(responses.get === List((200, false), (200, true)))
  }

  test("Create a listing repeats in case of server errors") {
    val accessTokenProvider = getSuccessfulAccessTokenProvider()
    val (responses, route) = mockServer(3)
    val api = getApiForServer(route, accessTokenProvider)

    api.create("1", testMode = false)

    assert(responses.get === List((503, false), (503, false), (503, false), (200, false)))
  }

  test("Create a listing repeats in case of exception") {
    val accessTokenProvider = mock(classOf[AccessTokenProvider])
    when(accessTokenProvider.getAccessToken()).thenReturn(Future.failed(new RuntimeException), Future.successful("token"))

    val (responses, route) = mockServer(0)
    val api = getApiForServer(route, accessTokenProvider)

    api.create("1", testMode = false)

    assert(responses.get === List((200, false)))
  }

  test("Delete a listing successfully") {
    val accessTokenProvider = getSuccessfulAccessTokenProvider()
    val (responses, route) = mockServer(0)
    val api = getApiForServer(route, accessTokenProvider)

    api.delete("1", testMode = false)
    api.delete("1", testMode = true)

    assert(responses.get === List((200, false), (200, true)))
  }

  test("Delete a listing repeats in case of server errors") {
    val accessTokenProvider = getSuccessfulAccessTokenProvider()
    val (responses, route) = mockServer(3)
    val api = getApiForServer(route, accessTokenProvider)

    api.delete("1", testMode = false)

    assert(responses.get === List((503, false), (503, false), (503, false), (200, false)))
  }

  test("Delete a listing repeats in case of exception") {
    val accessTokenProvider = mock(classOf[AccessTokenProvider])
    when(accessTokenProvider.getAccessToken()).thenReturn(Future.failed(new RuntimeException), Future.successful("token"))

    val (responses, route) = mockServer(0)
    val api = getApiForServer(route, accessTokenProvider)

    api.delete("1", testMode = false)

    assert(responses.get === List((200, false)))
  }

  test("404 is a successful delete") {
    val accessTokenProvider = getSuccessfulAccessTokenProvider()
    val (responses, route) = mockServer(0)
    val api = getApiForServer(route, accessTokenProvider)

    api.delete("no-such-listing", testMode = false)

    assert(responses.get === List((404, false)))
  }
}
