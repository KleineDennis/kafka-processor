#!/bin/bash

KAFKA_IMG='544725753551.dkr.ecr.eu-west-1.amazonaws.com/kafka/kafka:latest'

delete_topic() {
  name="${1}"

  docker run -it \
    --network local_cluster \
    $KAFKA_IMG /kafka/bin/kafka-topics.sh \
      --zookeeper zookeeper:2181 \
      --delete \
      --topic $name
}

delete_topic raw-listings
delete_topic listing-images-updates
delete_topic listing-enrichment-2-images
delete_topic listing-enrichment-2-images-dlq
