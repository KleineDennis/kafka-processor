#!/bin/bash

KAFKA_IMG='544725753551.dkr.ecr.eu-west-1.amazonaws.com/kafka/kafka:latest'

create_topic() {
  name="${1}"

  docker run -it \
    --network local_cluster \
    $KAFKA_IMG /kafka/bin/kafka-topics.sh \
      --zookeeper zookeeper:2181 \
      --create \
      --topic $name \
      --partitions 10 \
      --replication-factor 2 \
      --config cleanup.policy=delete \
      --config compression.type=uncompressed
}

create_topic raw-listings
create_topic listing-images
create_topic listing-enrichment-2-images
create_topic listing-enrichment-2-images-dlq
