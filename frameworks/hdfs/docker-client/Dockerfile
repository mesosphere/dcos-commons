FROM ubuntu:16.04

RUN apt-get -y update && \
    apt-get -y upgrade && \
    apt-get -y install wget software-properties-common

RUN wget https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.9.1-dcos.tar.gz && \
    tar xf hadoop-2.6.0-cdh5.9.1-dcos.tar.gz && \
    rm hadoop-2.6.0-cdh5.9.1-dcos.tar.gz

RUN apt-get update && \
    apt-get install -y default-jre && \
    apt-get install -y dnsutils

COPY configure-hdfs.sh /configure-hdfs.sh
RUN chmod +x configure-hdfs.sh
ENV HDFS_SERVICE_NAME hdfs
ENV JAVA_HOME /usr/lib/jvm/default-java
RUN echo "/configure-hdfs.sh" >> ~/.bashrc
WORKDIR /hadoop-2.6.0-cdh5.9.1
