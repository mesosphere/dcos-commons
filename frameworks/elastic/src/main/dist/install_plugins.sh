#!/bin/bash

PLUGINS="file://$MESOS_SANDBOX/x-pack-$ELASTIC_VERSION.zip"
IFS=","

if [ -n "$ELASTICSEARCH_PLUGINS" ]; then
    PLUGINS="$PLUGINS$IFS$ELASTICSEARCH_PLUGINS"
fi

for PLUGIN in $PLUGINS; do
    echo "Installing plugin: $PLUGIN from directory: "`pwd`
    echo "with command: ./elasticsearch-$ELASTIC_VERSION/bin/elasticsearch-plugin install --batch $PLUGIN"
    ./elasticsearch-$ELASTIC_VERSION/bin/elasticsearch-plugin install --batch $PLUGIN
done
