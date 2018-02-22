#!/bin/bash

usage() {
    echo "HDFS_SERVICE_NAME=hdfs /$HADOOP_VERSION/configure-hdfs.sh"
    exit 1
}

[[ -n "${HDFS_SERVICE_NAME}" ]] || usage

cd /$HADOOP_VERSION
wget -P /$HADOOP_VERSION/ "api.${HDFS_SERVICE_NAME}.marathon.l4lb.thisdcos.directory:80/v1/endpoints/core-site.xml" >> /$HADOOP_VERSION/configure-hdfs.log 2>&1
wget -P /$HADOOP_VERSION/ "api.${HDFS_SERVICE_NAME}.marathon.l4lb.thisdcos.directory:80/v1/endpoints/hdfs-site.xml" >> /$HADOOP_VERSION/configure-hdfs.log 2>&1
cp /$HADOOP_VERSION/core-site.xml /$HADOOP_VERSION/etc/hadoop >> /$HADOOP_VERSION/configure-hdfs.log 2>&1
cp /$HADOOP_VERSION/hdfs-site.xml /$HADOOP_VERSION/etc/hadoop >> /$HADOOP_VERSION/configure-hdfs.log 2>&1
