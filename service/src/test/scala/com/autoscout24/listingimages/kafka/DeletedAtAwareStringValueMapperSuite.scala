package com.autoscout24.listingimages.kafka

import org.apache.kafka.common.errors.SerializationException
import org.scalatest.FunSuite

class DeletedAtAwareStringValueMapperSuite extends FunSuite {
  test("Map some test data to empty string") {
    assert(DeleteAtAndTestModeAwareValueMapper("""{"foo":"bar"}""") === "")
  }

  test("Map testmode listings to tm=t") {
    assert(DeleteAtAndTestModeAwareValueMapper("""{"testMode":true}""") === "tm=t")
  }

  test("Map object with deletedAt set to null") {
    assert(DeleteAtAndTestModeAwareValueMapper("""{"deletedAt":"true"}""") === null)
  }

  test("Map object with empty deletedAt to empty String") {
    assert(DeleteAtAndTestModeAwareValueMapper("""{"deletedAt":null}""") === "")
  }

  test("Map empty object to empty String") {
    assert(DeleteAtAndTestModeAwareValueMapper("{}") === "")
  }

  test("Map null to null") {
    assert(DeleteAtAndTestModeAwareValueMapper(null) === null)
  }

  test("Deserialization fails") {
    val exception = intercept[SerializationException] {
      DeleteAtAndTestModeAwareValueMapper("{")
    }

    assert(exception.getMessage === "Failed to deserialize as JsObject 'Some({)'")
  }
}
