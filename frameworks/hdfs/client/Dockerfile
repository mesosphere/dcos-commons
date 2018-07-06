FROM ubuntu:16.04

# the env var is required for a non-interactive install of krb5-user
RUN export DEBIAN_FRONTEND=noninteractive && \
    apt-get -y update && \
    apt-get -y upgrade && \
    apt-get -y install \
      curl \
      default-jre \
      dnsutils \
      krb5-user \
      less \
      software-properties-common \
      vim \
      unzip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV HADOOP_VERSION hadoop-2.6.0-cdh5.9.1
ENV HDFS_SERVICE_NAME hdfs
ENV JAVA_HOME /usr/lib/jvm/default-java

ARG HADOOP_TAR_ARCHIVE="$HADOOP_VERSION-dcos.tar.gz"
RUN curl -O -L https://downloads.mesosphere.com/hdfs/assets/${HADOOP_TAR_ARCHIVE} && \
    tar xf ${HADOOP_TAR_ARCHIVE} && \
    rm ${HADOOP_TAR_ARCHIVE}

RUN chown -R nobody:nogroup /$HADOOP_VERSION && \
    chown -R nobody:nogroup /etc

COPY krb5.conf /etc/krb5.conf

COPY configure-environment.sh /$HADOOP_VERSION/configure-environment.sh
COPY configure-hdfs.sh /$HADOOP_VERSION/configure-hdfs.sh

WORKDIR "/$HADOOP_VERSION/"

# this script will setup the HDFS configuration regardless of HDFS being kerberized or not
CMD "/$HADOOP_VERSION/configure-environment.sh"
