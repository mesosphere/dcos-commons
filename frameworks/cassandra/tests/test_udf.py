import retrying
from typing import Any, Dict, Iterator, List
import logging
import pytest
import sdk_jobs
import sdk_cmd
import sdk_install
import sdk_tasks
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    test_jobs: List[Dict[str, Any]] = []
    try:
        test_jobs = config.get_udf_jobs()
        # destroy/reinstall any prior leftover jobs, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.install_job(job)

        # service_options = {
        #     "service": {
        #         "name": config.SERVICE_NAME,
        #         "security": {"authentication": {"enabled": True}, "authorization": {"enabled": True}},
        #     },
        #     "cassandra": {
        #          "enable_user_defined_functions": True,
        #          "enable_scripted_user_defined_functions": True,
        #     }
        # }

        # sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        # sdk_install.install(
        #     config.PACKAGE_NAME,
        #     config.SERVICE_NAME,
        #     config.DEFAULT_TASK_COUNT,
        #     additional_options=service_options,
        # )

        yield  # let the test session execute
    finally:
        # sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        # remove job definitions from metronome
        for job in test_jobs:
            sdk_jobs.remove_job(job)


@pytest.mark.sanity
def test_udf() -> None:
    config.verify_client_can_write_read_udf(
        config.get_foldered_node_address(),
    )

