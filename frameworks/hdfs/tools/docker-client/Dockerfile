FROM ubuntu:16.04

RUN apt-get -y update && \
    apt-get -y upgrade && \
    apt-get -y install wget software-properties-common

RUN wget https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.9.1-dcos.tar.gz && \
    tar xf hadoop-2.6.0-cdh5.9.1-dcos.tar.gz && \
    rm hadoop-2.6.0-cdh5.9.1-dcos.tar.gz

RUN apt-get update && \
    apt-get install -y default-jre && \
    apt-get install -y \
    curl \
    dnsutils \
    less \
    vim

ENV HADOOP_VERSION hadoop-2.6.0-cdh5.9.1
ENV HDFS_SERVICE_NAME hdfs
ENV JAVA_HOME /usr/lib/jvm/default-java

# the env var is required for a non-interactive install of krb5-user
RUN export DEBIAN_FRONTEND=noninteractive && apt-get install -y krb5-user
COPY krb5.conf /etc/krb5.conf
RUN chown -R nobody /etc

COPY configure-environment.sh /hadoop-2.6.0-cdh5.9.1/configure-environment.sh
COPY configure-hdfs.sh /hadoop-2.6.0-cdh5.9.1/configure-hdfs.sh

# working directory will be the HDFS distribution
RUN chown -R nobody /hadoop-2.6.0-cdh5.9.1

WORKDIR /hadoop-2.6.0-cdh5.9.1

# this script will setup the HDFS configuration regardless of HDFS being kerberized or not
CMD "./configure-environment.sh"
