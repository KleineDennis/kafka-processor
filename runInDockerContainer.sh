#!/bin/bash
echo "Running command \`$@\` inside docker container..."
docker-compose -f ./docker/docker-compose.yml run --rm --service-ports listing-images-kafka-processor bash -c "$@"