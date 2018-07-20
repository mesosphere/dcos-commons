#!/bin/bash

DOCKER_TAG=${DOCKER_TAG:-latest}
DOCKER_IMAGE=${DOCKER_IMAGE:-mesosphere/dcos-commons:${DOCKER_TAG}}

if [ x"$1" == x"--format" ]; then
    volume_mode="rw"
    black_cmd="black"
    shift
else
    volume_mode="ro"
    black_cmd="black --check"
fi

docker run --rm -t \
    -v $(pwd):/build:${volume_mode} \
    -u $(id -u):$(id -g) \
    -w /build \
        ${DOCKER_IMAGE} \
            ${black_cmd} "$@"
