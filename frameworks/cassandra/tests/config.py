""" Utilties specific to Cassandra tests """

import os
import logging
import textwrap
import traceback
from typing import Any, Dict, List, Optional

import sdk_hosts
import sdk_jobs
import sdk_plan
import sdk_utils

log = logging.getLogger(__name__)

PACKAGE_NAME = "cassandra"

CASSANDRA_DOCKER_IMAGE = "cassandra:3.11.3"

SERVICE_NAME = os.environ.get("SOAK_SERVICE_NAME") or "cassandra"

DEFAULT_TASK_COUNT = 3
DEFAULT_CASSANDRA_TIMEOUT = 600
# Soak artifact scripts may override the service name to test

DEFAULT_NODE_ADDRESS = os.getenv(
    "CASSANDRA_NODE_ADDRESS", sdk_hosts.autoip_host(SERVICE_NAME, "node-0-server")
)
DEFAULT_NODE_PORT = os.getenv("CASSANDRA_NODE_PORT", "9042")


def get_foldered_service_name() -> str:
    return sdk_utils.get_foldered_name(SERVICE_NAME)


def get_foldered_node_address() -> str:
    return sdk_hosts.autoip_host(get_foldered_service_name(), "node-0-server")


def _get_cqlsh_tls_rc_config(
    node_address: str,
    node_port: str,
    certfile: str = "/mnt/mesos/sandbox/ca-bundle.crt",
) -> str:
    """
    Returns a content of `cqlshrc` configuration file with provided hostname,
    port and certfile location. The configuration can be used for connecting
    to cassandra over a TLS connection.
    """
    return textwrap.dedent(
        """
        [connection]
        hostname = {hostname}
        port = {port}
        factory = cqlshlib.ssl.ssl_transport_factory

        [ssl]
        certfile = {certfile}
        ; Note: If validate = true then the certificate name must match the machine's hostname
        validate = true
        ; If using client authentication (require_client_auth = true in cassandra.yaml) you'll also need to point to your uesrkey and usercert.
        ; SSL client authentication is only supported via cqlsh on C* 2.1 and greater.
        ; This is disabled by default on all Instaclustr-managed clusters.
        ; userkey = /path/to/userkey.pem
        ; usercert = /path/to/usercert.pem
        """.format(
            hostname=node_address, port=node_port, certfile=certfile
        )
    )


def _get_test_job(
    name: str,
    commands: List[str],
    node_address: str,
    node_port: str,
    restart_policy: str="ON_FAILURE",
    dcos_ca_bundle: Optional[str]=None,
) -> Dict[str, Any]:
    if dcos_ca_bundle:
        commands.insert(
            0,
            " && ".join(
                [
                    'echo -n "$CQLSHRC_FILE" > $MESOS_SANDBOX/cqlshrc',
                    'echo -n "$CA_BUNDLE" > $MESOS_SANDBOX/ca-bundle.crt',
                ]
            ),
        )
    job = {
        "description": "{} with restart policy {}".format(name, restart_policy),
        "id": "test.cassandra." + name,
        "run": {
            "cmd": " && ".join(commands),
            "docker": {"image": CASSANDRA_DOCKER_IMAGE},
            "cpus": 1,
            "mem": 512,
            "disk": 100,
            "user": "nobody",
            "restart": {"policy": restart_policy},
        },
    }  # type: Dict[str, Any]
    if dcos_ca_bundle:
        job["run"]["env"] = {
            "CQLSHRC_FILE": _get_cqlsh_tls_rc_config(node_address, node_port),
            "CA_BUNDLE": dcos_ca_bundle,
        }
        # insert --cqlshrc and --ssl args into any cqlsh commands:
        job["run"]["cmd"] = job["run"]["cmd"].replace(
            "cqlsh -e", 'cqlsh --cqlshrc="$MESOS_SANDBOX/cqlshrc" --ssl -e'
        )
    return job


def _cqlsh(query: str, node_address: str, node_port: str) -> str:
    return 'cqlsh -e "{}" {} {}'.format(query, node_address, node_port)


def get_delete_data_job(
    node_address: str = DEFAULT_NODE_ADDRESS,
    node_port: str = DEFAULT_NODE_PORT,
    dcos_ca_bundle: Optional[str] = None,
) -> Dict[str, Any]:
    cql = " ".join(
        [
            "DROP TABLE IF EXISTS testspace1.testtable1;",
            "DROP TABLE IF EXISTS testspace2.testtable2;",
            "DROP KEYSPACE IF EXISTS testspace1;",
            "DROP KEYSPACE IF EXISTS testspace2;",
        ]
    )
    return _get_test_job(
        "delete-data-retry",
        [_cqlsh(cql, node_address, node_port)],
        node_address,
        node_port,
        dcos_ca_bundle=dcos_ca_bundle,
    )


def get_verify_data_job(
    node_address: str = DEFAULT_NODE_ADDRESS,
    node_port: str = DEFAULT_NODE_PORT,
    dcos_ca_bundle: Optional[str] = None,
) -> Dict[str, Any]:
    cmds = [
        "{} | grep testkey1".format(
            _cqlsh("SELECT * FROM testspace1.testtable1;", node_address, node_port)
        ),
        "{} | grep testkey2".format(
            _cqlsh("SELECT * FROM testspace2.testtable2;", node_address, node_port)
        ),
    ]
    return _get_test_job(
        "verify-data", cmds, node_address, node_port, dcos_ca_bundle=dcos_ca_bundle
    )


def get_verify_deletion_job(
    node_address: str = DEFAULT_NODE_ADDRESS,
    node_port: str = DEFAULT_NODE_PORT,
    dcos_ca_bundle: Optional[str] = None,
) -> Dict[str, Any]:
    cmds = [
        '{} | grep "0 rows"'.format(
            _cqlsh(
                "SELECT * FROM system_schema.tables WHERE keyspace_name='testspace1';",
                node_address,
                node_port,
            )
        ),
        '{} | grep "0 rows"'.format(
            _cqlsh(
                "SELECT * FROM system_schema.tables WHERE keyspace_name='testspace2';",
                node_address,
                node_port,
            )
        ),
    ]
    return _get_test_job(
        "verify-deletion", cmds, node_address, node_port, dcos_ca_bundle=dcos_ca_bundle
    )


def get_write_data_job(
    node_address: str = DEFAULT_NODE_ADDRESS,
    node_port: str = DEFAULT_NODE_PORT,
    dcos_ca_bundle: Optional[str] = None,
) -> Dict[str, Any]:
    cql = " ".join(
        [
            "CREATE KEYSPACE testspace1 WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };",
            "USE testspace1;",
            "CREATE TABLE testtable1 (key varchar, value varchar, PRIMARY KEY(key));",
            "INSERT INTO testspace1.testtable1(key, value) VALUES('testkey1', 'testvalue1');",
            "CREATE KEYSPACE testspace2 WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };",
            "USE testspace2;",
            "CREATE TABLE testtable2 (key varchar, value varchar, PRIMARY KEY(key));",
            "INSERT INTO testspace2.testtable2(key, value) VALUES('testkey2', 'testvalue2');",
        ]
    )
    return _get_test_job(
        "write-data",
        [_cqlsh(cql, node_address, node_port)],
        node_address,
        node_port,
        dcos_ca_bundle=dcos_ca_bundle,
    )


def get_all_jobs(
    node_address: str = DEFAULT_NODE_ADDRESS,
    node_port: str = DEFAULT_NODE_PORT,
) -> List[Dict[str, Any]]:
    return [
        get_write_data_job(node_address),
        get_verify_data_job(node_address),
        get_delete_data_job(node_address),
        get_verify_deletion_job(node_address),
    ]


def run_backup_and_restore(
    service_name: str,
    backup_plan: str,
    restore_plan: str,
    plan_parameters: Dict[str, Optional[str]],
    job_node_address:  str = DEFAULT_NODE_ADDRESS,
) -> None:
    write_data_job = get_write_data_job(node_address=job_node_address)
    verify_data_job = get_verify_data_job(node_address=job_node_address)
    delete_data_job = get_delete_data_job(node_address=job_node_address)
    verify_deletion_job = get_verify_deletion_job(node_address=job_node_address)

    # Ensure the keyspaces we will use aren't present. In practice this should run once and fail
    # because the data isn't present. When the job is flagged as failed (due to restart=NEVER),
    # the run_job() call will throw.
    try:
        sdk_jobs.run_job(delete_data_job)
    except Exception:
        log.info("Error during delete (normal if no stale data)")
        log.info(traceback.format_exc())

    # Write data to Cassandra with a metronome job, then verify it was written
    # Note: Write job will fail if data already exists
    sdk_jobs.run_job(write_data_job)
    sdk_jobs.run_job(verify_data_job)

    # Run backup plan, uploading snapshots and schema to the cloudddd
    sdk_plan.start_plan(service_name, backup_plan, parameters=plan_parameters)
    sdk_plan.wait_for_completed_plan(service_name, backup_plan)

    # Delete all keyspaces and tables with a metronome job
    sdk_jobs.run_job(delete_data_job)

    # Verify that the keyspaces and tables were deleted
    sdk_jobs.run_job(verify_deletion_job)

    # Run restore plan, retrieving snapshots and schema from S3
    sdk_plan.start_plan(service_name, restore_plan, parameters=plan_parameters)
    sdk_plan.wait_for_completed_plan(service_name, restore_plan)

    # Verify that the data we wrote and then deleted has been restored
    sdk_jobs.run_job(verify_data_job)

    # Delete data in preparation for any other backup tests
    sdk_jobs.run_job(delete_data_job)
    sdk_jobs.run_job(verify_deletion_job)
