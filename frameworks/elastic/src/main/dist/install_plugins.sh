#!/bin/bash

PLUGINS="file://$MESOS_SANDBOX/x-pack-$ELASTIC_VERSION.zip,file://$MESOS_SANDBOX/elasticsearch-statsd-$ELASTIC_VERSION-SNAPSHOT.zip"
IFS=","

if [ -n "$ELASTICSEARCH_PLUGINS" ]; then
    PLUGINS="$PLUGINS$IFS$ELASTICSEARCH_PLUGINS"
fi

for PLUGIN in $PLUGINS; do
    echo "Installing plugin: $PLUGIN"
    echo "./elasticsearch-$ELASTIC_VERSION/bin/elasticsearch-plugin install --batch $PLUGIN"
#    ./elasticsearch-$ELASTIC_VERSION/bin/elasticsearch-plugin install --batch $PLUGIN
done
