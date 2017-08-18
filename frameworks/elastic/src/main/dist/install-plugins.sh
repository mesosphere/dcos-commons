#!/bin/bash

PLUGINS=""
IFS=","

# If a plugin fails to install, exit the script immediately with an error
set -e

if [ -n "$ELASTICSEARCH_PLUGINS" ]; then
    PLUGINS="$ELASTICSEARCH_PLUGINS"
fi

if [ "$XPACK_ENABLED" = true ]; then
    XPACK_PLUGIN="file://$MESOS_SANDBOX/x-pack-$ELASTIC_VERSION.zip"
    if [ -n "$PLUGINS" ]; then
        PLUGINS="$PLUGINS$IFS$XPACK_PLUGIN"
    else
        PLUGINS="$XPACK_PLUGIN"
    fi
fi

if [ -n "$STATSD_UDP_HOST" ]; then
    STATSD_PLUGIN="file://$MESOS_SANDBOX/elasticsearch-statsd-$ELASTIC_VERSION.0.zip"
    if [ -n "$PLUGINS" ]; then
        PLUGINS="$PLUGINS$IFS$STATSD_PLUGIN"
    else
        PLUGINS="$STATSD_PLUGIN"
    fi
fi

for PLUGIN in ${PLUGINS}; do
    echo "Installing plugin: $PLUGIN"
    ./elasticsearch-$ELASTIC_VERSION/bin/elasticsearch-plugin install --batch ${PLUGIN}
done
