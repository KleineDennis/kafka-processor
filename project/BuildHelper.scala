object BuildHelper {
  def getListingImagesClientSecret = {
      Option(System.getProperty("listing-images.client-secret"))
        .getOrElse(throw new RuntimeException("Please set listing-images.client-secret" ))
  }
}
