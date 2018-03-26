import logging
import retrying

import sdk_cmd
import sdk_hosts

from security import kerberos


LOG = logging.getLogger(__name__)


USERS = [
        "client",
        "authorized",
        "unauthorized",
        "super"
]


def get_service_principals(service_name: str, realm: str, custom_domain: str = None) -> list:
    """
    Sets up the appropriate principals needed for a kerberized deployment of HDFS.
    :return: A list of said principals
    """
    primaries = ["kafka", ]

    tasks = [
        "kafka-0-broker",
        "kafka-1-broker",
        "kafka-2-broker",
    ]

    if custom_domain:
        instances = map(lambda task: sdk_hosts.custom_host(service_name, task, custom_domain), tasks)
    else:
        instances = map(lambda task: sdk_hosts.autoip_host(service_name, task), tasks)

    principals = kerberos.generate_principal_list(primaries, instances, realm)
    principals.extend(kerberos.generate_principal_list(USERS, [None, ], realm))

    return principals


def is_not_authorized(output: str) -> bool:
    return "AuthorizationException: Not authorized to access" in output


def get_kerberos_client_properties(ssl_enabled: bool) -> list:

    protocol = "SASL_SSL" if ssl_enabled else "SASL_PLAINTEXT"

    return ['security.protocol={protocol}'.format(protocol=protocol),
            'sasl.mechanism=GSSAPI',
            'sasl.kerberos.service.name=kafka', ]


def get_ssl_client_properties(cn: str, has_kerberos: bool) -> list:

    if has_kerberos:
        client_properties = []
    else:
        client_properties = ["security.protocol=SSL", ]

    client_properties.extend(["ssl.truststore.location = {cn}_truststore.jks".format(cn=cn),
                              "ssl.truststore.password = changeit",
                              "ssl.keystore.location = {cn}_keystore.jks".format(cn=cn),
                              "ssl.keystore.password = changeit", ])

    return client_properties


def write_client_properties(id: str, marathon_task: str, lines: list) -> str:
    """Write a client properties file containing the specified lines"""

    output_file = "{id}-client.properties".format(id=id)

    LOG.info("Generating %s", output_file)
    output = sdk_cmd.create_task_text_file(marathon_task, output_file, lines)
    LOG.info(output)

    return output_file


def write_jaas_config_file(primary: str, marathon_task: str, krb5: object) -> str:
    output_file = "{primary}-client-jaas.config".format(primary=primary)

    LOG.info("Generating %s", output_file)

    # TODO: use kafka_client keytab path
    jaas_file_contents = ['KafkaClient {',
                          '    com.sun.security.auth.module.Krb5LoginModule required',
                          '    doNotPrompt=true',
                          '    useTicketCache=true',
                          '    principal=\\"{primary}@{realm}\\"'.format(primary=primary, realm=krb5.get_realm()),
                          '    useKeyTab=true',
                          '    serviceName=\\"kafka\\"',
                          '    keyTab=\\"/tmp/kafkaconfig/kafka-client.keytab\\"',
                          '    client=true;',
                          '};', ]

    output = sdk_cmd.create_task_text_file(marathon_task, output_file, jaas_file_contents)
    LOG.info(output)

    return output_file


def setup_krb5_env(primary: str, marathon_task: str, krb5: object) -> str:
    env_setup_string = "export KAFKA_OPTS=\\\"" \
                       "-Djava.security.auth.login.config={} " \
                       "-Djava.security.krb5.conf={}" \
                       "\\\"".format(write_jaas_config_file(primary, marathon_task, krb5),
                                     kerberos.write_krb5_config_file(marathon_task, "krb5.config", krb5))
    LOG.info("Setting environment to %s", env_setup_string)
    return env_setup_string


def get_bash_command(cmd: str, environment: str) -> str:
    env_str = "{} && ".format(environment) if environment else ""

    return "bash -c \"{}{}\"".format(env_str, cmd)


def write_to_topic(cn: str, marathon_task: str, topic: str, message: str,
                   client_properties: list=[], environment: str=None,
                   broker_list: str="\$KAFKA_BROKER_LIST") -> bool:

    client_properties_file = write_client_properties(cn, marathon_task, client_properties)

    cmd_list = ["echo", message,
                "|",
                "kafka-console-producer",
                "--topic", topic,
                "--producer.config", client_properties_file,
                "--broker-list", broker_list,
                ]
    cmd = " ".join(str(c) for c in cmd_list)
    write_cmd = get_bash_command(cmd, environment)

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
        rc, stdout, stderr = sdk_cmd.marathon_task_exec(marathon_task, write_cmd)
        LOG.info("rc=%s\nstdout=%s\nstderr=%s\n", rc, stdout, stderr)

        return rc, stdout, stderr

    rc, stdout, stderr = write_wrapper()

    rc_success = rc is 0
    stdout_success = ">>" in stdout
    stderr_success = not is_not_authorized(stderr)

    return rc_success and stdout_success and stderr_success


def read_from_topic(cn: str, marathon_task: str, topic: str, messages: int,
                    client_properties: list=[], environment: str=None,
                    broker_list: str="\$KAFKA_BROKER_LIST") -> str:

    client_properties_file = write_client_properties(cn, marathon_task, client_properties)

    cmd_list = ["kafka-console-consumer",
                "--topic", topic,
                "--consumer.config", client_properties_file,
                "--bootstrap-server", broker_list,
                "--from-beginning",
                "--max-messages", messages,
                "--timeout-ms", 60000,
                ]

    cmd = " ".join(str(c) for c in cmd_list)
    read_cmd = get_bash_command(cmd, environment)

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
        rc, stdout, stderr = sdk_cmd.marathon_task_exec(marathon_task, read_cmd)
        LOG.info("rc=%s\nstdout=%s\nstderr=%s\n", rc, stdout, stderr)

        return rc, stdout, stderr

    output = read_wrapper()

    assert output[0] is 0
    return " ".join(str(o) for o in output)
