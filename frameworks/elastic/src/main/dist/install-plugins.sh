#!/bin/bash

PLUGINS="file://$MESOS_SANDBOX/x-pack-$ELASTIC_VERSION.zip"
IFS=","

if [ -n "$ELASTICSEARCH_PLUGINS" ]; then
    PLUGINS="$PLUGINS$IFS$ELASTICSEARCH_PLUGINS"
fi

if [ -n "$STATSD_UDP_HOST" ]; then
    STATSD_PLUGIN="https://github.com/Automattic/elasticsearch-statsd-plugin/releases/download/$ELASTIC_VERSION.0/elasticsearch-statsd-$ELASTIC_VERSION.0.zip"
    PLUGINS="$PLUGINS$IFS$STATSD_PLUGIN"
fi

for PLUGIN in ${PLUGINS}; do
    echo "Installing plugin: $PLUGIN"
    ./elasticsearch-$ELASTIC_VERSION/bin/elasticsearch-plugin install --batch ${PLUGIN}
done
