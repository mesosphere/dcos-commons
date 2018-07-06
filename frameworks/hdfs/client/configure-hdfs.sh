#!/bin/bash

usage() {
    echo "HDFS_SERVICE_NAME=hdfs /$HADOOP_VERSION/configure-hdfs.sh"
    exit 1
}

[[ -n "${HDFS_SERVICE_NAME}" ]] || usage

cd /$HADOOP_VERSION
wget -O /$HADOOP_VERSION/etc/hadoop/core-site.xml "api.${HDFS_SERVICE_NAME}.marathon.l4lb.thisdcos.directory:80/v1/endpoints/core-site.xml" >> /$HADOOP_VERSION/configure-hdfs.log 2>&1
wget -O /$HADOOP_VERSION/etc/hadoop/hdfs-site.xml "api.${HDFS_SERVICE_NAME}.marathon.l4lb.thisdcos.directory:80/v1/endpoints/hdfs-site.xml" >> /$HADOOP_VERSION/configure-hdfs.log 2>&1
