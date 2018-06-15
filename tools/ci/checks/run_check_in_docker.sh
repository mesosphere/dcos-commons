#!/bin/bash

DOCKER_TAG=${DOCKER_TAG:-latest}
DOCKER_IMAGE=${DOCKER_IMAGE:-mesosphere/dcos-commons:${DOCKER_TAG}}


TOOL=$1
shift

docker run --rm -t \
    -v $(pwd):/build:ro \
    -w /build \
        mesosphere/dcos-commons:elezar-dev \
            ${TOOL} $*
