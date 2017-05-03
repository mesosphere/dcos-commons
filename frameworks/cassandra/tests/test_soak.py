import json

import pytest

from tests.config import (
    DEFAULT_TASK_COUNT,
    DELETE_DATA_JOB,
    PACKAGE_NAME,
    install_cassandra_jobs,
    launch_and_verify_job,
    remove_cassandra_jobs,
)
from tests.test_backup import run_backup_and_restore
import sdk_test_upgrade


def setup_module(module):
    install_cassandra_jobs()


def teardown_module(module):
    remove_cassandra_jobs()


@pytest.mark.soak_backup
def test_backup_and_restore():
    run_backup_and_restore()

    # Since this is run on the soak cluster and state is retained, we have to
    # also delete the test data in preparation for the next run.
    launch_and_verify_job(DELETE_DATA_JOB, expected_successes=2)


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    """Install the Cassandra Universe package and attempt upgrade to master.
    
    Assumes that the install options file is placed in the repo root."""
    with open('cassandra.json') as options_file:
        install_options = json.load(options_file)

    sdk_test_upgrade.soak_upgrade_downgrade(
        PACKAGE_NAME, DEFAULT_TASK_COUNT, install_options
    )
