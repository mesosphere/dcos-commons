import logging
import retrying
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


def is_not_authorized(output: str) -> bool:
    return "AuthorizationException: Not authorized to access" in output


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


def write_to_topic(cn: str, task: str, topic: str, message: str, cmd: str=None) -> str:
    if not cmd:
        env_str = setup_env(cn, task)
        client_properties = write_client_properties(cn, task)

        write_cmd = "bash -c \"{} && echo {} | kafka-console-producer \
            --topic {} \
            --producer.config {} \
            --broker-list \$KAFKA_BROKER_LIST\"".format(env_str,
                                                        message,
                                                        topic,
                                                        client_properties)
    else:
        write_cmd = cmd

    def write_failed(output) -> bool:
        LOG.info("Checking write output: %s", output)
        rc = output[0]
        stderr = output[2]

        if rc:
            LOG.error("Write failed with non-zero return code")
            return True
        if "UNKNOWN_TOPIC_OR_PARTITION" in stderr:
            LOG.error("Write failed due to stderr: UNKNOWN_TOPIC_OR_PARTITION")
            return True
        if "LEADER_NOT_AVAILABLE" in stderr and "ERROR Error when sending message" in stderr:
            LOG.error("Write failed due to stderr: LEADER_NOT_AVAILABLE")
            return True

        LOG.info("Output check passed")

        return False

    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=60 * 1000,
                    retry_on_result=write_failed)
    def write_wrapper():
        LOG.info("Running: %s", write_cmd)
        rc, stdout, stderr = sdk_tasks.task_exec(task, write_cmd)
        LOG.info("rc=%s\nstdout=%s\nstderr=%s\n", rc, stdout, stderr)

        return rc, stdout, stderr

    rc, stdout, stderr = write_wrapper()

    rc_success = rc is 0
    stdout_success = ">>" in stdout
    stderr_success = not is_not_authorized(stderr)

    return rc_success and stdout_success and stderr_success


def read_from_topic(cn: str, task: str, topic: str, messages: int, cmd: str=None) -> str:
    if not cmd:
        env_str = setup_env(cn, task)
        client_properties = write_client_properties(cn, task)
        timeout_ms = 60000
        read_cmd = "bash -c \"{} && kafka-console-consumer \
            --topic {} \
            --consumer.config {} \
            --bootstrap-server \$KAFKA_BROKER_LIST \
            --from-beginning --max-messages {} \
            --timeout-ms {} \
            \"".format(env_str, topic, client_properties, messages, timeout_ms)
    else:
        read_cmd = cmd

    def read_failed(output) -> bool:
        LOG.info("Checking read output: %s", output)
        rc = output[0]
        stderr = output[2]

        if rc:
            LOG.error("Read failed with non-zero return code")
            return True
        if "kafka.consumer.ConsumerTimeoutException" in stderr:
            return True

        LOG.info("Output check passed")

        return False

    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=60 * 1000,
                    retry_on_result=read_failed)
    def read_wrapper():
        LOG.info("Running: %s", read_cmd)
        rc, stdout, stderr = sdk_tasks.task_exec(task, read_cmd)
        LOG.info("rc=%s\nstdout=%s\nstderr=%s\n", rc, stdout, stderr)

        return rc, stdout, stderr

    output = read_wrapper()

    assert output[0] is 0
    return " ".join(str(o) for o in output)
