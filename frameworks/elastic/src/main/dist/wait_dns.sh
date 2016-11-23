#!/bin/bash          
for INDEX in 0 1 2; do
    HOST="master-$INDEX-server.$SERVICE_NAME.mesos"
    while nslookup $HOST | grep "server can't find" > /dev/null; do
        echo "Waiting for $HOST to resolve..."
        sleep 5
    done
done
