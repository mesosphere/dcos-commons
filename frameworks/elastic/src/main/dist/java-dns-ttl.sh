#!/bin/bash

JAVA_SECURITY_FILE=$JAVA_HOME/lib/security/java.security

sed -i'' -e '/networkaddress.cache.ttl/ s/#//;s/-1/10/' $JAVA_SECURITY_FILE
