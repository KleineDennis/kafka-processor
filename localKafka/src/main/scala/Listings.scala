import play.api.libs.json.Json

case class Listing(id: Int, category: String, description: String, offset: Long)

// This is not how the actual raw listing looks like. But we don't care. We just merge it.
case class RawListing(id: Int, description: String)

object Formats {

  implicit val rawListingFormat = Json.format[RawListing]

  implicit val listingFormat = Json.format[Listing]

}

