package com.autoscout24.listingimages.kafka

import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.streams.kstream.ValueMapper
import play.api.libs.json.{JsObject, Json}
import scala.util.{Success, Try}

/**
  * Deserializes to an empty String, if data is a JsObject without a DeletedAt attribute or null otherwise.
  */
object DeleteAtAndTestModeAwareValueMapper extends ValueMapper[String, String] {

  override def apply(value: String): String = Try {
    Option(value).map(Json.parse)
  } match {
    case Success(Some(jsObject: JsObject)) =>
      (jsObject \ "deletedAt").asOpt[String] match {
        case Some(s) if s.nonEmpty => null
        case _ => (jsObject \ "testMode").asOpt[Boolean].map(testMode => if(testMode) "tm=t" else "").getOrElse("")
      }
    case Success(None) => null
    case other =>
      throw new SerializationException("Failed to deserialize as JsObject '" + Option(value).map(new String(_)) + "'")
  }
}