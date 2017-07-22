#!/bin/bash

usage() {
    echo "HDFS_SERVICE_NAME=hdfs configure-hdfs.sh"
    exit 1
}

[[ -n "${HDFS_SERVICE_NAME}" ]] || usage

cd /
wget -P / "api.${HDFS_SERVICE_NAME}.marathon.l4lb.thisdcos.directory:80/v1/endpoints/core-site.xml" >>/configure-hdfs.log 2>&1
wget -P / "api.${HDFS_SERVICE_NAME}.marathon.l4lb.thisdcos.directory:80/v1/endpoints/hdfs-site.xml" >>/configure-hdfs.log 2>&1
cp /core-site.xml /hadoop-2.6.0-cdh5.9.1/etc/hadoop >>/configure-hdfs.log 2>&1
cp /hdfs-site.xml /hadoop-2.6.0-cdh5.9.1/etc/hadoop >>/configure-hdfs.log 2>&1
