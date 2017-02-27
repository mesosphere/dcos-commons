#!/bin/sh

$MESOS_SANDBOX/mongodb-linux-x86_64-$MONGODB_VERSION/bin/mongod --port $MONGODB_PORT --dbpath $MESOS_SANDBOX/mongodb-replicaset &
sleep 5
$MESOS_SANDBOX/mongodb-linux-x86_64-$MONGODB_VERSION/bin/mongo mongodb:///tmp/mongodb-$MONGODB_PORT.sock --eval "db.getSiblingDB('admin').createUser(
  {
    user: 'admin',
    pwd: '$MONGODB_PASSWORD',
    roles: [
      { role: 'root', db: 'admin' }
    ]
  }
)"
$MESOS_SANDBOX/mongodb-linux-x86_64-$MONGODB_VERSION/bin/mongo -u admin -p $MONGODB_PASSWORD mongodb:///tmp/mongodb-$MONGODB_PORT.sock/admin --eval "db.shutdownServer()"
