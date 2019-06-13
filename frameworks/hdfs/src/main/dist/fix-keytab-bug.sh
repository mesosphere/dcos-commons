#!/bin/sh
set -x
export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jdk*/)
$JAVA_HOME/bin/java -jar krbtest.jar $1
echo "Keytab file fixed due to bug in HDFS version 3.2.0. See HADOOP-16283 https://issues.apache.org/jira/plugins/servlet/mobile#issue/HADOOP-16283"
