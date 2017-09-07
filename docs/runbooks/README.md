# General Instructions

  * [DataDog Timeboard](https://app.datadoghq.com/dash/332120/foundation-services-listing-images-kafka-processor)
  * AWS Console
    * [Log in as as24iam](https://as24iam.signin.aws.amazon.com/console)
    * [Switch to ReadOnlyAccess@as24prod role](https://signin.aws.amazon.com/switchrole?account=as24prod&roleName=ReadOnlyAccess)
  * CloudWatch
  * [Metric](https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#metrics:)

# In Case of an Incident:

  * Breathe and DON'T PANIC!
  * Consider the context:
    * what happened before the incident (or is still going on)? A deployment?
      A failure? A power outage?
  * Check all the metrics that might be relevant. What looks normal/plausible?
    What doesn't?
  * Note down all symptoms and things you change
  * Don't make assumptions, check. Think outside of the box. Look at things from
    a different angle
  * Talk to co-workers
  * Listen to your gut feeling

# Impact of Service Outage

If this service becomes unavailable, the consequence is that the listing-images
database will not be in sync with the listings in the raw-listings topic.

| Effect  | Fix within  | Description  |
|---|---|---|
| No image restore  | 2 days  | Soft-deleted images will not be restored if the corresponding listing appears in raw-listings. Soft-deleted images are retained for two days, and will be deleted after this grace period. If restoring the images is delayed by more than two days, the images will be lost. |
| Image deletion delayed  | days or weeks  | Images will not be deleted if the listing is deleted.  |
| Workload accumulation | one week | Once the service comes back up, it will have to process all the work that has been queued while it was down. As it will query the listing-images API, longer outages will have an impact on the load of the API. |

# Standard Operating Procedure (SOP)

## No Progress Alarm

There are incoming records, but the processor does not make progress. Try to
restart the instances by triggering a deployment.

## Low Free JVM Memory Alarm

The JVM on one or more processor instances are running low on free memory. Add
more instances manually. The sizing of the processor at the time of
implementation was such that it could manage up to four times the amount of
listings at that time.

## Low Free Disk Space Alarm

One or more processor instances are running low on disk. Trigger a re-deployment
of the service manually.
