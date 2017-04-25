import pytest

from tests.config import *
from tests.test_backup import run_backup_and_restore


def setup_module(module):
    install_cassandra_jobs()


def teardown_module(module):
    remove_cassandra_jobs()


@pytest.mark.soak_backup
def test_backup_and_restore():
    run_backup_and_restore()

    # Since this is run on the soak cluster and state is retained, we have to also delete the test
    # data in preparation for the next run.
    launch_and_verify_job(DELETE_DATA_JOB, expected_successes=2)
