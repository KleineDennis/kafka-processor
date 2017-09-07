ListingImages Kafka processor
=============================

This service take care to delete listing images when a listing is deleted from the raw-listing topic.
It joins the "listing-images" and "raw-listings" topics and informs the listing-images service about deleted listings
which will cause the images of that listing to be deleted as well.

NB: This is only a soft-delete. The real deletion from S3 and DynamoDB is deferred and uses DDB time-to-live feature.
Details about this can be found in the architecture overview of the listing-images service.

Development
===========

Testing
-------

To test run

`./sbt test`

Run project on local machine
----------------------------

Start local kafka and fake producers

`./sbt localKafka/run`

This will create random listings and images for these listings.

Then run the service

`./sbt -Dlisting-images.client-secret=[decrypted-dev-secret] service/run`

For the dev secret, see rakefile. Use KMS to decrypt the secret. This only works properly if you also have a locally
running instance of the listing-images service on localhost:9000. 

Known issues
============
* No testmode support for raw-listings, since the raw-listings topic doesn't support it yet. Thus all testmode images
  will automatically be deleted after two days. 

Owner
=====

Team owning this repository: Foundation services
