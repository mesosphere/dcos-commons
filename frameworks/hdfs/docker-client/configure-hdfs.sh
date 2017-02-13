#!/bin/bash

usage() {
    echo "HDFS_SERVICE_NAME=hdfs configure-hdfs.sh"
    exit 1
}

[[ -n "${HDFS_SERVICE_NAME}" ]] || usage

API_PORT=$(dig _${HDFS_SERVICE_NAME}._tcp.marathon.mesos SRV +short | cut -d " " -f 3)
wget "${HDFS_SERVICE_NAME}.marathon.mesos:${API_PORT}/v1/endpoints/core-site.xml"
wget "${HDFS_SERVICE_NAME}.marathon.mesos:${API_PORT}/v1/endpoints/hdfs-site.xml"
cp core-site.xml /hadoop-2.6.0-cdh5.9.1/etc/hadoop
cp hdfs-site.xml /hadoop-2.6.0-cdh5.9.1/etc/hadoop
