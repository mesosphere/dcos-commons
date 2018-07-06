#!/bin/bash

export PATH=$PATH:/usr/local/sbin:/usr/sbin:/sbin

sed -e "s/KDC_ADDRESS/$KDC_ADDRESS/g" -i /etc/krb5.conf
sed -e "s/REALM/$REALM/g" -i /etc/krb5.conf
echo "Configured kerberos conf file"

/$HADOOP_VERSION/configure-hdfs.sh
echo "Configured HDFS"
echo "Spawning infinite sleep"
while true; do sleep 1000000; done
