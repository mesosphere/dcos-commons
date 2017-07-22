#!/bin/bash

usage() {
    echo "HDFS_SERVICE_NAME=hdfs configure-hdfs.sh"
    exit 1
}

[[ -n "${HDFS_SERVICE_NAME}" ]] || usage

cd /
wget -P / "api.hdfs.marathon.l4lb.thisdcos.directory:80/v1/endpoints/core-site.xml"
wget -P / "api.hdfs.marathon.l4lb.thisdcos.directory:80/v1/endpoints/hdfs-site.xml"
cp /core-site.xml /hadoop-2.6.0-cdh5.9.1/etc/hadoop
cp /hdfs-site.xml /hadoop-2.6.0-cdh5.9.1/etc/hadoop
