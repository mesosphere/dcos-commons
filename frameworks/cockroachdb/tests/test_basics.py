import pytest
import shakedown
import time

import sdk_install as install
import sdk_utils as utils
import sdk_cmd as cmd
import sdk_tasks as tasks

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    DEFAULT_POD_TYPE,
    DEFAULT_TASK_NAME,
    SERVICE_NAME
)

def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)

def teardown_module(module):
    install.uninstall(PACKAGE_NAME)

def cockroach_cmd(sql_command, database='', task='cockroachdb-1-node-join'):
    ''' Generates dcos command that can be passed
    to sdk_cmd.run_cli for testing. '''
    dcos_command = """task exec {} \
        ./cockroach sql \
        -e "{}" --insecure \
        --host=internal.cockroachdb.l4lb.thisdcos.directory""".format(task, sql_command)
    if database:
        dcos_command += ' -d {}'.format(database)
    return dcos_command

def cockroach_nodes_healthy(task='cockroachdb-1-node-join'):
    ''' Executes `cockroach node ls` on the
    first CockroachDB node to confirm that
    the number of active, connected nodes
    matches the DEFAULT_TASK_COUNT. '''
    cmd_node_ls = "task exec {} \
        ./cockroach node ls \
        --host='internal.cockroachdb.l4lb.thisdcos.directory' \
        --insecure".format(task)
    out_node_ls = cmd.run_cli(cmd_node_ls)
    return '{} row'.format(DEFAULT_TASK_COUNT) in out_node_ls

@pytest.mark.smoke
@pytest.mark.sanity
def test_install():
    pass

@pytest.mark.smoke
@pytest.mark.sanity
def test_unique_hosts():
    service_ips = shakedown.get_service_ips(SERVICE_NAME)
    print("service_ips: " + str(service_ips))
    assert len(service_ips) == len(set(service_ips))

@pytest.mark.smoke
@pytest.mark.sanity
def test_read_write():
    # Generate SQL Commands
    cmd_drop_database = cockroach_cmd('DROP DATABASE IF EXISTS bank;')
    cmd_create_database = cockroach_cmd('CREATE DATABASE bank;')
    cmd_create_table = cockroach_cmd('CREATE TABLE accounts (id INT PRIMARY KEY, balance INT);', 'bank')
    cmd_insert = cockroach_cmd('INSERT INTO accounts (id, balance) VALUES (1, 1000), (2, 250);', 'bank')
    cmd_select = cockroach_cmd('SELECT id, balance FROM accounts;', 'bank')

    # Run SQL Commands
    cmd.run_cli(cmd_drop_database)
    out_create_database = cmd.run_cli(cmd_create_database)
    out_create_table = cmd.run_cli(cmd_create_table)
    out_insert = cmd.run_cli(cmd_insert)
    out_select = cmd.run_cli(cmd_select)

    # Confirm Output
    assert 'CREATE DATABASE' in out_create_database
    assert 'CREATE TABLE' in out_create_table
    assert 'INSERT 2' in out_insert
    assert '2 rows' in out_select

@pytest.mark.smoke
@pytest.mark.sanity
def test_nodes_connected():
    # Generate SQL Commands
    cmd_drop_database = cockroach_cmd('DROP DATABASE IF EXISTS bank;')
    cmd_create_database = cockroach_cmd('CREATE DATABASE bank;')
    cmd_create_table = cockroach_cmd('CREATE TABLE accounts (id INT PRIMARY KEY, balance INT);', 'bank')
    cmd_insert = cockroach_cmd('INSERT INTO accounts (id, balance) VALUES (1, 1000), (2, 250);', 'bank')
    cmd_select = cockroach_cmd('SELECT id, balance FROM accounts;', 'bank', task='cockroachdb-2-node-join')

    # Run SQL Commands
    cmd.run_cli(cmd_drop_database)
    out_create_database = cmd.run_cli(cmd_create_database)
    out_create_table = cmd.run_cli(cmd_create_table)
    out_insert = cmd.run_cli(cmd_insert)
    out_select = cmd.run_cli(cmd_select)

    # Confirm Output
    assert 'CREATE DATABASE' in out_create_database
    assert 'CREATE TABLE' in out_create_table
    assert 'INSERT 2' in out_insert
    assert '2 rows' in out_select

@pytest.mark.smoke
@pytest.mark.sanity
def test_tasks_updated():
    service_ips = shakedown.get_service_ips(SERVICE_NAME)
    old_task_ids = tasks.get_task_ids(SERVICE_NAME, 'cockroach')
    for service_ip in service_ips:
        shakedown.kill_process_on_host(service_ip, "cockroach start")                                # Kill CockroachDB node
        tasks.check_running(SERVICE_NAME, DEFAULT_TASK_COUNT, 5*60)                                  # Wait for new CockroachDB node to run
        shakedown.wait_for(lambda: cockroach_nodes_healthy(), noisy=True, timeout_seconds=5*60)      # Wait for healthy CockroachDB cluster
    tasks.check_tasks_updated(SERVICE_NAME, 'cockroach', old_task_ids)

@pytest.mark.smoke
@pytest.mark.sanity
def test_data_survives_crash():
    # Generate SQL Commands
    cmd_drop_database = cockroach_cmd('DROP DATABASE IF EXISTS bank;')
    cmd_create_database = cockroach_cmd('CREATE DATABASE bank;')
    cmd_create_table = cockroach_cmd('CREATE TABLE accounts (id INT PRIMARY KEY, balance INT);', 'bank')
    cmd_insert = cockroach_cmd('INSERT INTO accounts (id, balance) VALUES (1, 1000), (2, 250);', 'bank')
    cmd_select = cockroach_cmd('SELECT id, balance FROM accounts;', 'bank')

    # Run SQL Commands (except cmd_select)
    cmd.run_cli(cmd_drop_database)
    out_create_database = cmd.run_cli(cmd_create_database)
    out_create_table = cmd.run_cli(cmd_create_table)
    out_insert = cmd.run_cli(cmd_insert)

    # Kill All CockroachDB Nodes (one at a time)
    service_ips = shakedown.get_service_ips(SERVICE_NAME)
    for service_ip in service_ips:
        shakedown.kill_process_on_host(service_ip, "cockroach start")                                # Kill CockroachDB node
        tasks.check_running(SERVICE_NAME, DEFAULT_TASK_COUNT, 5*60)                                  # Wait for new CockroachDB node to run
        shakedown.wait_for(lambda: cockroach_nodes_healthy(), noisy=True, timeout_seconds=5*60)      # Wait for healthy CockroachDB cluster
        time.sleep(30)                                                                               # Give CockroachDB time to replicate data

    # Run cmd_select
    out_select = cmd.run_cli(cmd_select)

    # Confirm Output
    assert '2 rows' in out_select

