package com.autoscout24.listingimages.kafka

import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.Materializer
import com.autoscout24.listingimages.kafka.Is24AccessTokenProvider._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

/**
  * Provides an access token for accessing the listing-images API.
  */
trait AccessTokenProvider {
  def getAccessToken(): Future[String]
}

object Is24AccessTokenProvider {
  implicit val accessTokenReads = Json.reads[AccessToken]

  sealed trait CacheItem

  final case class CacheItemLoadingInProgress(isReady: Promise[AccessToken]) extends CacheItem

  final case class CacheItemReady(expiresAt: DateTime, accessToken: AccessToken) extends CacheItem

  case class AccessToken(access_token: String, token_type: String, expires_in: Long, scope: String, kid: String, jti: String)

}

/**
  * Provides an access token that allows to delete and restore listings.
  *
  * The access token is automatically refreshed every 15 minutes. If the refresh fails, there is a simple retry
  * mechanism keeps trying to get a new access token on failures.
  *
  * When a consumer tries to get an access token, while there is non available yet, the access token future is not
  * fulfilled until a token get available.
  */
class Is24AccessTokenProvider(clientConfig: ListingImagesClientConfig, implicit val actorSystem: ActorSystem, implicit val materializer: Materializer) extends AccessTokenProvider {
  val logger = LoggerFactory.getLogger(this.getClass)

  private val tokenCache = new AtomicReference[CacheItem](CacheItemLoadingInProgress(Promise[AccessToken]()))

  @tailrec
  final def getAccessToken(): Future[String] = tokenCache.get() match {
    case CacheItemLoadingInProgress(p) =>
      p.future.map(_.access_token)
    case ci @ CacheItemReady(expiresAt, _) =>
      if (expiresAt.getMillis - DateTime.now().getMillis < 30000) {
        val promise = Promise[AccessToken]()
        tokenCache.compareAndSet(ci, CacheItemLoadingInProgress(promise))
        getAccessToken()
      } else {
        Future.successful(ci.accessToken.access_token)
      }
  }

  protected def getFreshAccessToken(): Future[AccessToken] = {
    logger.info(s"Create OAuth access token with clientId ${clientConfig.id} from ${clientConfig.authorizationServer}/oauth/token?grant_type=client_credentials")

    val request = HttpRequest()
      .withMethod(HttpMethods.POST)
      .withHeaders(Authorization(BasicHttpCredentials(clientConfig.id, clientConfig.secret)))
      .withUri(s"${clientConfig.authorizationServer}/oauth/token?grant_type=client_credentials")

    for {
      response <- Http().singleRequest(request)
      httpEntity <- response.entity.toStrict(60.seconds)
    } yield {
      if (response.status.intValue() == 200 || response.status.intValue() == 201) {
        Json.fromJson[AccessToken](Json.parse(httpEntity.getData().decodeString(Charset.forName("UTF-8")).getBytes)) match {
          case JsSuccess(token, path) => token
          case JsError(error) => throw new RuntimeException("Failed to deserialize listing-images accessToken " + error)
        }
      } else {
        val message = httpEntity.getData().decodeString(Charset.forName("UTF-8"))
        throw new RuntimeException("Retrieving listing-images accessToken failed" + response.status + ": " + message)
      }
    }
  }

  protected def sleepBetweenRetries() = Thread.sleep(15000)

  def tryToRefreshAccessToken(attemptsLeft: Int): Runnable = () => {
    logger.info("Update listing-images accessToken")
    val now = DateTime.now()
    getFreshAccessToken().map { token =>
      tokenCache.getAndSet(CacheItemReady(now.plusSeconds(token.expires_in.toInt), token)) match {
        case CacheItemLoadingInProgress(promise) => promise.trySuccess(token)
        case _ => ()
      }
      logger.info("Successfully updated listing-images accessToken")
    }.failed.foreach { e: Throwable =>
      if (attemptsLeft > 0) {
        logger.warn("Failed to update listing-images accessToken", e)
        sleepBetweenRetries()
        tryToRefreshAccessToken(attemptsLeft - 1).run()
      } else
        logger.error("Give up retrying accessToken updates", e)
    }
  }

  actorSystem.scheduler.schedule(0.seconds, 15.minutes, tryToRefreshAccessToken(50))
}