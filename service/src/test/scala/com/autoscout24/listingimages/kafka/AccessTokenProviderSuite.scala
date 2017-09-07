package com.autoscout24.listingimages.kafka

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.mockito.{ArgumentMatchers => M}
import org.mockito.Mockito._
import org.mockito.Mockito.spy
import org.scalatest.FunSuite
import scala.language.reflectiveCalls

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

class AccessTokenProviderSuite extends FunSuite {

  def mockAuthorizationServer(numberOfErrors: Int) = {
    val count = new AtomicInteger(0)

    path("oauth" / "token") {
      post {
        extractRequest { request =>
          val currentRequestNumber = count.getAndIncrement()
          val authorization = request.headers.find(_.is("authorization")).map(_.value()).getOrElse("")
          parameters('grant_type.as[String]) { grantType =>
            (authorization, grantType) match {
              case _ if currentRequestNumber < numberOfErrors =>
                complete(HttpResponse(503, entity = "Internal server error"))
              case ("Basic Y2xpZW50OnNlY3JldA==", "client_credentials") =>
                val accessToken = """{"access_token":"secret-token","token_type":"bearer","expires_in":3599,"scope":"as24-listing-images-delete-restore","kid":"tuv_2016-02-02T15:36:41.457Z","jti":"6c87e9ff-68c7-413b-ad3c-b6a5b332b787"}"""
                complete(HttpEntity(ContentTypes.`application/json`, accessToken))
            }
          }
        }
      }
    }
  }

  test("Successfully retrieves access token from authorization server") {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val bindingFuture = Http().bindAndHandle(mockAuthorizationServer(0), "localhost", 0)
    val port = Await.result(bindingFuture, 10.seconds).localAddress.getPort
    val clientConfig = ListingImagesClientConfig("listing-images-service", "client", "secret", s"http://localhost:$port")
    val accessTokenProvider = new Is24AccessTokenProvider(clientConfig, actorSystem, materializer)

    val result = Await.result(accessTokenProvider.getAccessToken(), 10.seconds)

    assert(result === "secret-token")
  }

  test("Access token is reloaded every 15 minutes") {
    val actorSystem: ActorSystem = mock(classOf[ActorSystem])
    val scheduler = mock(classOf[Scheduler])
    when(actorSystem.scheduler).thenReturn(scheduler)
    val materializer = ActorMaterializer()(ActorSystem())

    val clientConfig = ListingImagesClientConfig("listing-images-service", "client", "secret", s"http://localhost:666")
    val accessTokenProvider = new Is24AccessTokenProvider(clientConfig, actorSystem, materializer)

    verify(scheduler, times(1)).schedule(M.eq(0.seconds), M.eq(15.minutes), M.any())(M.any())
  }

  test("AccessTokenProvider repeats attempt to get an access token in case of errors") {
    implicit val actorSystem: ActorSystem = spy(ActorSystem())
    implicit val materializer = ActorMaterializer()

    val bindingFuture = Http().bindAndHandle(mockAuthorizationServer(10), "localhost", 0)
    val port = Await.result(bindingFuture, 10.seconds).localAddress.getPort
    val clientConfig = ListingImagesClientConfig("listing-images-service", "client", "secret", s"http://localhost:$port")
    val accessTokenProvider = new Is24AccessTokenProvider(clientConfig, actorSystem, materializer) {
      val retryCounter = new AtomicInteger(0)
      override def sleepBetweenRetries() = {
        retryCounter.incrementAndGet()
      }
    }

    val result = Await.result(accessTokenProvider.getAccessToken(), 10.seconds)

    assert(result === "secret-token")
    assert(accessTokenProvider.retryCounter.get() === 10)
  }
}
