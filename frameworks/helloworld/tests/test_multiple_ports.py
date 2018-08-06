import pytest
import logging

import sdk_install
import sdk_tasks

from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_launch_task_with_multiple_ports():
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        0,
        additional_options={"service": {"yaml": "multiport"}},
    )
    assert (
        sdk_tasks.get_completed_task_id("multiport-0-server") is not None
    ), "Unable to find completed task id"
