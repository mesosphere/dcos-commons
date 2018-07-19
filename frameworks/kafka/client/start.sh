#!/usr/bin/env bash

KAFKA_KERBEROS_OPTS="-Djava.security.auth.login.config=/tmp/kafkaconfig/client-jaas.conf -Djava.security.krb5.conf=/tmp/kafkaconfig/krb5.conf"

if [ x"$KAFKA_KERBEROS_DEBUG" != x ]; then
KAFKA_KERBEROS_OPTS="$KAFKA_KERBEROS_OPTS -Dsun.security.krb5.debug=true"
fi

KAFKA_OPTS=${KAFKA_OPTS:-$KAFKA_KERBEROS_OPTS}

export KAFKA_OPTS

KAFKA_SERVICE_NAME=${KAFKA_SERVICE_NAME:-kafka}
KAFKA_CLIENT_MODE=${KAFKA_CLIENT_MODE:-consumer}
KAFKA_TOPIC=${KAFKA_TOPIC:-securetest}
KAFKA_BROKER_LIST=${KAFKA_BROKER_LIST:-"kafka-0-broker.${KAFKA_SERVICE_NAME}.autoip.dcos.thisdcos.directory:1025,kafka-1-broker.${KAFKA_SERVICE_NAME}.autoip.dcos.thisdcos.directory:1025,kafka-2-broker.${KAFKA_SERVICE_NAME}.autoip.dcos.thisdcos.directory:1025"}

if [ $# -gt 0 ]; then
    KAFKA_CLIENT_MODE=$1
fi

if [ $# -gt 1 ]; then
    shift
    MESSAGE=$*
fi

if [ "$KAFKA_CLIENT_MODE" == "producer" ]; then
    echo "Starting producer with:"
    echo "  KAFKA_BROKER_LIST=${KAFKA_BROKER_LIST}"
    echo "  KAFKA_TOPIC=${KAFKA_TOPIC}"

    while :; do
        if [ -z $MESSAGE ]; then
            msg="This is a secure test at $(date)"
        else
            msg="$MESSAGE"
        fi

        echo "$msg" | kafka-console-producer \
            --broker-list ${KAFKA_BROKER_LIST} \
        --topic ${KAFKA_TOPIC} \
        --producer.config /tmp/kafkaconfig/client.properties && echo "Sent message: '$msg'"

        if [ -z $MESSAGE ]; then
            sleep 10
        else
            exit 0
        fi
    done

elif [ "$KAFKA_CLIENT_MODE" == "consumer" ]; then
    echo "Starting consumer with:"
    echo "  KAFKA_BROKER_LIST=${KAFKA_BROKER_LIST}"
    echo "  KAFKA_TOPIC=${KAFKA_TOPIC}"

    if [ -z $MESSAGE ]; then
        echo "Starting tail consumer"
        kafka-console-consumer \
            --bootstrap-server ${KAFKA_BROKER_LIST} \
            --topic ${KAFKA_TOPIC} --from-beginning \
            --consumer.config /tmp/kafkaconfig/client.properties
    else
        echo "Getting single message"
        kafka-console-consumer \
            --bootstrap-server ${KAFKA_BROKER_LIST} \
            --topic securetest --from-beginning --max-messages 1 \
            --timeout-ms 60000 \
            --consumer.config /tmp/kafkaconfig/client.properties
    fi


elif [ "$KAFKA_CLIENT_MODE" == "test" ]; then
    echo "Starting TEST client:"
    env
    while :; do
        sleep 100000
    done
else
    echo "Unrecognised KAFKA_CLIENT_MODE"
fi
