#!/usr/bin/env bash

set -e

JMX_SECURE_PROPERTIES_FILE=$MESOS_SANDBOX/jmx_properties/jmxremote.ssl.properties

umask u=rw,g=,o=

NODETOOL_SSL_PROPERTIES_FILE=$MESOS_SANDBOX/.cassandra/nodetool-ssl.properties

tee $NODETOOL_SSL_PROPERTIES_FILE <<EOF >/dev/null

-Dcom.sun.management.jmxremote.ssl=true
-Dcom.sun.management.jmxremote.ssl.need.client.auth=true
-Dcom.sun.management.jmxremote.registry.ssl=true 
-Djavax.net.ssl.keyStore=$KEY_STORE_PATH
-Djavax.net.ssl.keyStorePassword=$SECURE_JMX_KEY_STORE_KEY 
-Djavax.net.ssl.trustStore=$TRUST_STORE_PATH
-Djavax.net.ssl.trustStorePassword=$SECURE_JMX_TRUST_STORE_KEY 
-Djavax.rmi.ssl.client.enabledProtocols=TLSv1.2

EOF

sed -i 's/    LOCAL_JMX=yes*/    LOCAL_JMX=no/' apache-cassandra-$CASSANDRA_VERSION/conf/cassandra-env.sh
sed -i '/  JVM_OPTS="$JVM_OPTS -Dcom.sun.management.jmxremote.rmi.port*/s/$JMX_PORT/"'$SECURE_JMX_RMI_PORT'"/g' apache-cassandra-$CASSANDRA_VERSION/conf/cassandra-env.sh 
#sed -i 's/# JVM_OPTS="$JVM_OPTS -Djava.rmi.server.hostname=<public name>"/JVM_OPTS="$JVM_OPTS -Djava.rmi.server.hostname='$MESOS_CONTAINER_IP'"/g' apache-cassandra-$CASSANDRA_VERSION/conf/cassandra-env.sh
sed -i '/JVM_OPTS="$JVM_OPTS -Dcom.sun.management.jmxremote.password.file=*/s/\/etc\/cassandra\/jmxremote.password/jmx_properties\/key_file/g' apache-cassandra-$CASSANDRA_VERSION/conf/cassandra-env.sh

NODETOOL_SSL_PROPERTIES_FILE=$MESOS_SANDBOX/apache-cassandra-$CASSANDRA_VERSION/conf/cassandra-env.sh

tee -a $NODETOOL_SSL_PROPERTIES_FILE <<EOF

JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.ssl=true"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.enabled.protocols=TLSv1.2"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.local.only=false"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.registry.ssl=true"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote=true"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.host=$MESOS_CONTAINER_IP"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.ssl.need.client.auth=true"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.keyStore=$KEY_STORE_PATH"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.keyStorePassword=$SECURE_JMX_KEY_STORE_KEY"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.trustStore=$TRUST_STORE_PATH"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.trustStorePassword=$SECURE_JMX_TRUST_STORE_KEY"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.access.file=$MESOS_SANDBOX/jmx/access_file"

EOF