#!/bin/bash

PLUGINS=""
IFS=","

readonly XPACK_PLUGIN="file://$MESOS_SANDBOX/x-pack-$ELASTIC_VERSION.zip"
readonly STATSD_PLUGIN="file://$MESOS_SANDBOX/elasticsearch-statsd-$ELASTIC_VERSION.0.zip"

# If a plugin fails to install, exit the script immediately with an error
set -e

if [ -n "$ELASTICSEARCH_PLUGINS" ]; then
    PLUGINS="$ELASTICSEARCH_PLUGINS"
fi

if [ "$XPACK_ENABLED" = true ] && [ -f "$XPACK_PLUGIN" ]; then
    if [ -n "$PLUGINS" ]; then
        PLUGINS="$PLUGINS$IFS$XPACK_PLUGIN"
    else
        PLUGINS="$XPACK_PLUGIN"
    fi
fi

if [ -f "$STATSD_ENABLED" ] && [ -n "$STATSD_UDP_HOST" ] && [ -f "$STATSD_PLUGIN" ]; then
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
