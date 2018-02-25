FROM ubuntu:16.04

ENV HADOOP_VERSION hadoop-2.6.0-cdh5.9.1
ENV HDFS_SERVICE_NAME hdfs
ENV JAVA_HOME /usr/lib/jvm/default-java

RUN apt-get -y update && \
    apt-get -y upgrade && \
    apt-get -y install \
      curl \
      default-jre \
      dnsutils \
      less \
      software-properties-common \
      vim \
      wget && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN wget https://downloads.mesosphere.com/hdfs/assets/$HADOOP_VERSION-dcos.tar.gz && \
    tar xf $HADOOP_VERSION-dcos.tar.gz && \
    rm $HADOOP_VERSION-dcos.tar.gz


# the env var is required for a non-interactive install of krb5-user
RUN export DEBIAN_FRONTEND=noninteractive && \
    apt-get update && \
    apt-get install -y krb5-user && \
    apt-get clean

COPY krb5.conf /etc/krb5.conf

COPY configure-environment.sh /$HADOOP_VERSION/configure-environment.sh
COPY configure-hdfs.sh /$HADOOP_VERSION/configure-hdfs.sh

WORKDIR /$HADOOP_VERSION

# this script will setup the HDFS configuration regardless of HDFS being kerberized or not
CMD "./configure-environment.sh"
