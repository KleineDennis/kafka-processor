package com.autoscout24.listingimages.kafka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, RawHeader}
import akka.stream.Materializer
import com.codahale.metrics.MetricRegistry
import com.google.common.util.concurrent.RateLimiter
import com.timgroup.statsd.StatsDClient
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Provides rate limited access to the listing-images API.
  */
class ListingImagesApiClient(clientConfig: ListingImagesClientConfig, accessTokenProvider: AccessTokenProvider)(implicit statsd: StatsDClient, implicit val actorSystem: ActorSystem, implicit val materializer: Materializer) {

  val rateLimiter: RateLimiter = RateLimiter.create(100)

  private val logger = LoggerFactory.getLogger(this.getClass)

  protected def backoff(attempt: Int): Unit = attempt match {
    case 0 => ()
    case 1 => Thread.sleep(10)
    case 2 => Thread.sleep(20)
    case 3 => Thread.sleep(50)
    case 4 => Thread.sleep(100)
    case _ => Thread.sleep(1000)
  }

  def create(listingId: String, testMode: Boolean): Unit = {
    def buildRequest(accessToken: String) = {
      val headers = if(testMode)
          Seq(Authorization(OAuth2BearerToken(accessToken)), RawHeader("X-Testmode", "true"))
        else
          Seq(Authorization(OAuth2BearerToken(accessToken)))

      HttpRequest()
        .withMethod(HttpMethods.POST)
        .withUri(s"${clientConfig.endpoint}/listings/$listingId")
        .withHeaders(headers :_*)
    }

    @tailrec
    def doRequest(attempt: Int): Unit = {
      rateLimiter.acquire()

      Try {
        val result = for {
          accessToken <- accessTokenProvider.getAccessToken()
          request = buildRequest(accessToken)
          result <- Http().singleRequest(request)
        } yield {
          result.discardEntityBytes()
          result
        }

        Await.result(result, 30.seconds)
      } match {
        case Success(r: HttpResponse) if r.status == StatusCodes.OK =>
          ()
        case Success(r: HttpResponse) =>
          logger.warn(s"Failed to create listing with id $listingId -> $r")
          backoff(attempt)
          doRequest(attempt + 1)
        case Failure(e) =>
          logger.warn(s"Exception while creating listing with id $listingId", e)
          backoff(attempt)
          doRequest(attempt + 1)
      }
    }

    doRequest(0)
    statsd.increment("create_listing_request", "target:image-api")
  }

  def delete(listingId: String, testMode: Boolean): Unit = {
    rateLimiter.acquire()

    def buildRequest(accessToken: String) = {
      val headers = if(testMode)
        Seq(Authorization(OAuth2BearerToken(accessToken)), RawHeader("X-Testmode", "true"))
      else
        Seq(Authorization(OAuth2BearerToken(accessToken)))

      HttpRequest()
        .withMethod(HttpMethods.DELETE)
        .withUri(s"${clientConfig.endpoint}/listings/$listingId")
        .withHeaders(headers :_*)
    }

    @tailrec
    def doRequest(attempt: Int): Unit = {
      Try {
        val result = for {
          accessToken <- accessTokenProvider.getAccessToken()
          request = buildRequest(accessToken)
          result <- Http().singleRequest(request)
        } yield {
          result.discardEntityBytes()
          result
        }

        Await.result(result, 30.seconds)
      } match {
        case Success(r: HttpResponse) if r.status == StatusCodes.OK || r.status == StatusCodes.NotFound =>
          ()
        case Success(r: HttpResponse) =>
          logger.warn(s"Failed to delete listing with id $listingId -> $r ($attempt)")
          backoff(attempt)
          doRequest(attempt + 1)
        case Failure(e) =>
          logger.warn(s"Exception while deleting listing with id $listingId ($attempt)", e)
          backoff(attempt)
          doRequest(attempt + 1)
      }
    }

    doRequest(0)
    statsd.increment("delete_listing_request", "target:image-api")
  }
}

