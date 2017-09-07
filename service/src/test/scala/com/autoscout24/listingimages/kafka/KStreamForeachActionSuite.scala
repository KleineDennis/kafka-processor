package com.autoscout24.listingimages.kafka

import com.codahale.metrics.{Counter, MetricRegistry}
import com.timgroup.statsd.StatsDClient
import org.scalatest.FunSuite
import org.mockito.Mockito._

class KStreamForeachActionSuite extends FunSuite {

  implicit val statsd = mock(classOf[StatsDClient])

  test("New raw listing is created") {
    val api = mock(classOf[ListingImagesApiClient])
    val action = new KStreamForeachAction(api)

    action.process("listing-1", ("", null))

    verify(api, times(1)).create("listing-1", false)
  }

  test("Images without a raw listing") {
    val api = mock(classOf[ListingImagesApiClient])
    val action = new KStreamForeachAction(api)

    action.process("listing-1", (null, ""))

    verify(api, times(1)).delete("listing-1", false)
  }

  test("Images without a raw listing with testmode=true") {
    val api = mock(classOf[ListingImagesApiClient])
    val action = new KStreamForeachAction(api)

    action.process("listing-1", (null, "tm=t"))

    verify(api, times(1)).delete("listing-1", true)
  }

  test("Nothing happens if raw listing and images exist") {
    val api = mock(classOf[ListingImagesApiClient])
    val action = new KStreamForeachAction(api)

    action.process("listing-1", ("", ""))

    verifyZeroInteractions(api)
  }

  test("Nothing happens if neither raw listing nor images exist") {
    val api = mock(classOf[ListingImagesApiClient])
    val action = new KStreamForeachAction(api)

    action.process("listing-1", (null, null))

    verifyZeroInteractions(api)
  }
}
