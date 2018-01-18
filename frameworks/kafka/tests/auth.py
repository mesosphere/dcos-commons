import json
import logging
import retrying

import sdk_cmd

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
    bootstrap_output = sdk_cmd.task_exec(client, ' '.join(bootstrap_cmd))
    LOG.info(bootstrap_output)
    assert "SDK Bootstrap successful" in ' '.join(str(bo) for bo in bootstrap_output)


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


def write_client_properties(id: str, task: str, lines: list) -> str:
    """Write a client properties file containing the specified lines"""

    output_file = "{id}-client.properties".format(id=id)

    LOG.info("Generating %s", output_file)
    output = sdk_cmd.create_task_text_file(task, output_file, lines)
    LOG.info(output)

    return output_file


def write_jaas_config_file(primary: str, task: str, krb5: object) -> str:
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

    output = sdk_cmd.create_task_text_file(task, output_file, jaas_file_contents)
    LOG.info(output)

    return output_file


def write_krb5_config_file(task: str, krb5: object) -> str:
    output_file = "krb5.config"

    LOG.info("Generating %s", output_file)

    try:
        # TODO: Set realm and kdc properties
        krb5_file_contents = ['[libdefaults]',
                              'default_realm = {}'.format(krb5.get_realm()),
                              '',
                              '[realms]',
                              '  {realm} = {{'.format(realm=krb5.get_realm()),
                              '    kdc = {}'.format(krb5.get_kdc_address()),
                              '  }', ]
        log.info("%s", krb5_file_contents)
    except Exception as e:
        log.error("%s", e)
        raise(e)

    output = sdk_cmd.create_task_text_file(task, output_file, krb5_file_contents)
    LOG.info(output)

    return output_file


def setup_krb5_env(primary: str, task: str, krb5: object) -> str:
    env_setup_string = "export KAFKA_OPTS=\\\"" \
                       "-Djava.security.auth.login.config={} " \
                       "-Djava.security.krb5.conf={}" \
                       "\\\"".format(write_jaas_config_file(primary, task, krb5), write_krb5_config_file(task, krb5))
    LOG.info("Setting environment to %s", env_setup_string)
    return env_setup_string


def get_bash_command(cmd: str, environment: str) -> str:
    env_str = "{} && ".format(environment) if environment else ""

    return "bash -c \"{}{}\"".format(env_str, cmd)


def write_to_topic(cn: str, task: str, topic: str, message: str,
                   client_properties: list=[], environment: str=None) -> bool:

    client_properties_file = write_client_properties(cn, task, client_properties)

    cmd = "echo {message} | kafka-console-producer \
            --topic {topic} \
            --producer.config {client_properties_file} \
            --broker-list \$KAFKA_BROKER_LIST".format(message=message,
                                                      topic=topic,
                                                      client_properties_file=client_properties_file)

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
        rc, stdout, stderr = sdk_cmd.task_exec(task, write_cmd)
        LOG.info("rc=%s\nstdout=%s\nstderr=%s\n", rc, stdout, stderr)

        return rc, stdout, stderr

    rc, stdout, stderr = write_wrapper()

    rc_success = rc is 0
    stdout_success = ">>" in stdout
    stderr_success = not is_not_authorized(stderr)

    return rc_success and stdout_success and stderr_success


def read_from_topic(cn: str, task: str, topic: str, messages: int,
                    client_properties: list=[], environment: str=None) -> str:

    client_properties_file = write_client_properties(cn, task, client_properties)

    cmd = "kafka-console-consumer \
            --topic {topic} \
            --consumer.config {client_properties_file} \
            --bootstrap-server \$KAFKA_BROKER_LIST \
            --from-beginning --max-messages {messages} \
            --timeout-ms {timeout_ms}".format(topic=topic,
                                              client_properties_file=client_properties_file,
                                              messages=messages,
                                              timeout_ms=60000)

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
        rc, stdout, stderr = sdk_cmd.task_exec(task, read_cmd)
        LOG.info("rc=%s\nstdout=%s\nstderr=%s\n", rc, stdout, stderr)

        return rc, stdout, stderr

    output = read_wrapper()

    assert output[0] is 0
    return " ".join(str(o) for o in output)


log = LOG


def create_tls_artifacts(cn: str, task: str) -> str:
    pub_path = "{}_pub.crt".format(cn)
    priv_path = "{}_priv.key".format(cn)
    log.info("Generating certificate. cn={}, task={}".format(cn, task))

    output = sdk_cmd.task_exec(
        task,
        'openssl req -nodes -newkey rsa:2048 -keyout {} -out request.csr '
        '-subj "/C=US/ST=CA/L=SF/O=Mesosphere/OU=Mesosphere/CN={}"'.format(priv_path, cn))
    log.info(output)
    assert output[0] is 0

    rc, raw_csr, _ = sdk_cmd.task_exec(task, 'cat request.csr')
    assert rc is 0
    request = {
        "certificate_request": raw_csr
    }

    token = sdk_cmd.run_cli("config show core.dcos_acs_token")

    output = sdk_cmd.task_exec(
        task,
        "curl --insecure -L -X POST "
        "-H 'Authorization: token={}' "
        "leader.mesos/ca/api/v2/sign "
        "-d '{}'".format(token, json.dumps(request)))
    log.info(output)
    assert output[0] is 0

    # Write the public cert to the client
    certificate = json.loads(output[1])["result"]["certificate"]
    output = sdk_cmd.task_exec(task, "bash -c \"echo '{}' > {}\"".format(certificate, pub_path))
    log.info(output)
    assert output[0] is 0

    create_keystore_truststore(cn, task)
    return "CN={},OU=Mesosphere,O=Mesosphere,L=SF,ST=CA,C=US".format(cn)


def create_keystore_truststore(cn: str, task: str):
    pub_path = "{}_pub.crt".format(cn)
    priv_path = "{}_priv.key".format(cn)
    keystore_path = "{}_keystore.jks".format(cn)
    truststore_path = "{}_truststore.jks".format(cn)

    log.info("Generating keystore and truststore, task:{}".format(task))
    output = sdk_cmd.task_exec(task, "curl -L -k -v leader.mesos/ca/dcos-ca.crt -o dcos-ca.crt")

    # Convert to a PKCS12 key
    output = sdk_cmd.task_exec(
        task,
        'bash -c "export RANDFILE=/mnt/mesos/sandbox/.rnd && '
        'openssl pkcs12 -export -in {} -inkey {} '
        '-out keypair.p12 -name keypair -passout pass:export '
        '-CAfile dcos-ca.crt -caname root"'.format(pub_path, priv_path))
    log.info(output)
    assert output[0] is 0

    log.info("Generating certificate: importing into keystore and truststore")
    # Import into the keystore and truststore
    output = sdk_cmd.task_exec(
        task,
        "keytool -importkeystore "
        "-deststorepass changeit -destkeypass changeit -destkeystore {} "
        "-srckeystore keypair.p12 -srcstoretype PKCS12 -srcstorepass export "
        "-alias keypair".format(keystore_path))
    log.info(output)
    assert output[0] is 0

    output = sdk_cmd.task_exec(
        task,
        "keytool -import -trustcacerts -noprompt "
        "-file dcos-ca.crt -storepass changeit "
        "-keystore {}".format(truststore_path))
    log.info(output)
    assert output[0] is 0
