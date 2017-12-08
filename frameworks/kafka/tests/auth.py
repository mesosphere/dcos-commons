import logging
import uuid

import sdk_tasks

LOG = logging.getLogger(__name__)


def wait_for_brokers(client: str, brokers: list):
    """
    Run bootstrap on the specified client to resolve the list of brokers
    """
    LOG.info("Running bootstrap to wait for DNS resolution")
    bootstrap_cmd = ['/opt/bootstrap',
                     '-print-env=false',
                     '-template=false',
                     '-install-certs=false',
                     '-resolve-hosts', ','.join(brokers)]
    bootstrap_output = sdk_tasks.task_exec(client, ' '.join(bootstrap_cmd))
    LOG.info(bootstrap_output)
    assert "SDK Bootstrap successful" in ' '.join(str(bo) for bo in bootstrap_output)


def send_and_receive_message(client: str):
    """
    Use the specified client to send a message and ensure that it can be recieved
    """
    LOG.info("Starting send-recieve test")
    message = uuid.uuid4()
    producer_cmd = ['/tmp/kafkaconfig/start.sh', 'producer', str(message)]

    for i in range(2):
        LOG.info("Running(%s) %s", i, producer_cmd)
        producer_output = sdk_tasks.task_exec(client, ' '.join(producer_cmd))
        LOG.info("Producer output(%s): %s", i, producer_output)

    assert "Sent message: '{message}'".format(message=str(
        message)) in ' '.join(str(p) for p in producer_output)

    consumer_cmd = ['/tmp/kafkaconfig/start.sh', 'consumer', 'single']
    LOG.info("Running %s", consumer_cmd)
    consumer_output = sdk_tasks.task_exec(client, ' '.join(consumer_cmd))
    LOG.info("Consumer output: %s", consumer_output)

    assert str(message) in ' '.join(str(c) for c in consumer_output)


def is_not_authorized(output: str) -> bool:
    return "Not authorized to access topics: [authz.test]" in output


def write_client_properties(primary: str, task: str) -> str:
    output_file = "{primary}-client.properties".format(primary=primary)
    LOG.info("Generating %s", output_file)

    output_cmd = """bash -c \"cat >{output_file} << EOL
security.protocol=SASL_PLAINTEXT
sasl.mechanism=GSSAPI
sasl.kerberos.service.name=kafka
EOL\"""".format(output_file=output_file, primary=primary)
    LOG.info("Running: %s", output_cmd)
    output = sdk_tasks.task_exec(task, output_cmd)
    LOG.info(output)

    return output_file


def write_jaas_config_file(primary: str, task: str) -> str:
    output_file = "{primary}-client-jaas.config".format(primary=primary)

    LOG.info("Generating %s", output_file)

    # TODO: use kafka_client keytab path
    output_cmd = """bash -c \"cat >{output_file} << EOL
KafkaClient {{
    com.sun.security.auth.module.Krb5LoginModule required
    doNotPrompt=true
    useTicketCache=true
    principal=\\"{primary}@LOCAL\\"
    useKeyTab=true
    serviceName=\\"kafka\\"
    keyTab=\\"/tmp/kafkaconfig/kafka-client.keytab\\"
client=true;
}};
EOL\"""".format(output_file=output_file, primary=primary)
    LOG.info("Running: %s", output_cmd)
    output = sdk_tasks.task_exec(task, output_cmd)
    LOG.info(output)

    return output_file


def write_krb5_config_file(task: str) -> str:
    output_file = "krb5.config"

    LOG.info("Generating %s", output_file)

    # TODO: Set realm and kdc properties
    output_cmd = """bash -c \"cat >{output_file} << EOL
[libdefaults]
default_realm = LOCAL

[realms]
  LOCAL = {{
    kdc = kdc.marathon.autoip.dcos.thisdcos.directory:2500
  }}
EOL\"""".format(output_file=output_file)
    LOG.info("Running: %s", output_cmd)
    output = sdk_tasks.task_exec(task, output_cmd)
    LOG.info(output)

    return output_file


def setup_env(primary: str, task: str) -> str:
    env_setup_string = "export KAFKA_OPTS=\\\"" \
                       "-Djava.security.auth.login.config={} " \
                       "-Djava.security.krb5.conf={}" \
                       "\\\"".format(write_jaas_config_file(primary, task), write_krb5_config_file(task))
    LOG.info("Setting environment to %s", env_setup_string)
    return env_setup_string


def write_to_topic(cn: str, task: str, topic: str, message: str) -> str:
    env_str = setup_env(cn, task)

    write_cmd = "bash -c \"{} && echo {} | kafka-console-producer \
        --topic {} \
        --producer.config {} \
        --broker-list \$KAFKA_BROKER_LIST\"".format(env_str, message, topic, write_client_properties(cn, task))

    LOG.info("Running: %s", write_cmd)
    output = sdk_tasks.task_exec(task, write_cmd)
    LOG.info(output)
    assert output[0] is 0
    return " ".join(str(o) for o in output)


def read_from_topic(cn: str, task: str, topic: str, messages: int) -> str:
    env_str = setup_env(cn, task)
    timeout_ms = 60000
    read_cmd = "bash -c \"{} && kafka-console-consumer \
        --topic {} \
        --consumer.config {} \
        --bootstrap-server \$KAFKA_BROKER_LIST \
        --from-beginning --max-messages {} \
        --timeout-ms {} \
        \"".format(env_str, topic, write_client_properties(cn, task), messages, timeout_ms)
    LOG.info("Running: %s", read_cmd)
    output = sdk_tasks.task_exec(task, read_cmd)
    LOG.info(output)
    assert output[0] is 0
    return " ".join(str(o) for o in output)
