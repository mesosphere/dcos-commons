#!/bin/bash
set -euo pipefail

# The function name is the command line arg to run it

APILIST="\
pre-test \
"

THIS_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
DOCKER_DIR="$THIS_SCRIPT_DIR/../src/main/docker"
SVC_YML="$THIS_SCRIPT_DIR/../src/main/dist/svc.yml"

### Public functions

pre-test() {
    DOCKER_IMAGE="$(get_docker_image_name)"
    LATEST_DOCKER_IMAGE="$(tagless_docker_image_name $DOCKER_IMAGE):latest"

    echo "DOCKER_IMAGE $DOCKER_IMAGE"
    echo "LATEST_DOCKER_IMAGE $LATEST_DOCKER_IMAGE"

    cd "$DOCKER_DIR"
    docker build -t "$LATEST_DOCKER_IMAGE" .
    docker tag "$LATEST_DOCKER_IMAGE" "$DOCKER_IMAGE"
    docker push "$LATEST_DOCKER_IMAGE"
    docker push "$DOCKER_IMAGE"
}

### Private functions

get_docker_image_name() {
    cat "$SVC_YML" | grep 'image-name: ' | grep 'proxylite' | sed -e 's/^.*image-name: \(.*\)/\1/'
}

tagless_docker_image_name() {
    docker_image="$1"

    echo "$(echo $docker_image | cut -d':' -f1)"
}

### main

comm=$1
shift
foundMatch=false
if [ -n "$comm" ]; then
    for func in $APILIST; do
        if [ "$comm" = "$func" ]; then
            foundMatch=true
        fi
    done
fi
if [ "$foundMatch" = true ]; then
    "${comm}" "$@"
else
    echo "$APILIST"
fi
