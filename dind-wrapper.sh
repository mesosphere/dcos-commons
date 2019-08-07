#!/bin/bash
set -e

echo "==> Launching the Docker daemon..."
if docker info > /dev/null 2>&1; then
    echo "=== Docker already running";
else
    /usr/local/bin/dind dockerd $DIND_DOCKER_ARGS > /var/log/docker.log 2>&1 &
    for i in `seq 300`; do
        echo "=== waiting for docker to start...";
        if docker info > /dev/null 2>&1; then
            break;
        fi;
        sleep 1;
    done;
    if docker info > /dev/null 2>&1; then
        echo "=== Docker started!";
    else
        echo "=== Docker failed to start";
        exit 1;
    fi;
fi

exec $@
