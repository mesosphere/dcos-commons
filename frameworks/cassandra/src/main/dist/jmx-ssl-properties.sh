#!/usr/bin/env bash

set -e

JMX_SECURE_PROPERTIES_FILE=$MESOS_SANDBOX/jmx_properties/jmxremote.ssl.properties

umask u=rw,g=,o=

tee $JMX_SECURE_PROPERTIES_FILE <<EOF >/dev/null

javax.net.ssl.keyStore=$KEY_STORE_PATH
javax.net.ssl.keyStorePassword=$SECURE_JMX_KEY_STORE_KEY
javax.net.ssl.trustStore=$TRUST_STORE_PATH
javax.net.ssl.trustStorePassword=$SECURE_JMX_TRUST_STORE_KEY
javax.net.ssl.enabled.protocols=TLSv1.2
EOF
	
JMX_PROPERTIES_FILE=$MESOS_SANDBOX/jmx_properties/jmx.properties

tee $JMX_PROPERTIES_FILE <<EOF >/dev/null

com.sun.management.jmxremote.host=$MESOS_CONTAINER_IP
com.sun.management.jmxremote=true
com.sun.management.jmxremote.authenticate=true
com.sun.management.jmxremote.rmi.port=$SECURE_JMX_RMI_PORT
com.sun.management.jmxremote.registry.ssl=true
com.sun.management.jmxremote.ssl.need.client.auth=true
com.sun.management.jmxremote.password.file=$MESOS_SANDBOX/jmx_properties/key_file
com.sun.management.jmxremote.access.file=$MESOS_SANDBOX/jmx/access_file
com.sun.management.jmxremote.ssl=true
com.sun.management.jmxremote.ssl.config.file=$MESOS_SANDBOX/jmx_properties/jmxremote.ssl.properties
com.sun.management.jmxremote.local.only=false


EOF

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