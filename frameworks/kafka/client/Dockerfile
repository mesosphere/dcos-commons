FROM confluentinc/cp-kafka:4.1.0

RUN apt-get update && \
    apt-get install --no-install-recommends -y \
        unzip

ARG JAVA_TAR_ARCHIVE="jre-8u152-linux-x64-jce-unlimited.tar.gz"
RUN cd /opt && \
    curl -O https://downloads.mesosphere.com/java/${JAVA_TAR_ARCHIVE} && \
	tar xzvf ${JAVA_TAR_ARCHIVE} && \
	rm ${JAVA_TAR_ARCHIVE}

ENV JAVA_HOME /opt/jre1.8.0_152

RUN cd /opt && \
    curl -O https://downloads.mesosphere.com/dcos-commons/artifacts/0.41.0/bootstrap.zip && \
	unzip bootstrap.zip && \
	rm bootstrap.zip

ENV KAFKA_CONFIG_ROOT /tmp/kafkaconfig
RUN mkdir -p $KAFKA_CONFIG_ROOT

COPY client-jaas.conf ${KAFKA_CONFIG_ROOT}
COPY client.properties ${KAFKA_CONFIG_ROOT}
COPY krb5.conf ${KAFKA_CONFIG_ROOT}


COPY start.sh ${KAFKA_CONFIG_ROOT}

CMD ${KAFKA_CONFIG_ROOT}/start.sh
