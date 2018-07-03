#!/bin/bash

DOCKER_TAG=${DOCKER_TAG:-latest}
DOCKER_IMAGE=${DOCKER_IMAGE:-mesosphere/dcos-commons:${DOCKER_TAG}}

docker run --rm -t \
    -v $(pwd):/build:ro \
    -w /build \
        ${DOCKER_IMAGE} \
            flake8 "$@"
