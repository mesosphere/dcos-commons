import logging
import pytest

import sdk_install
import sdk_tasks
from tests import config

log = logging.getLogger(__name__)

NUM_HELLO = 2


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {
            "service": {
                "test_profile_volume": "df -t xfs",
                "yaml": "pod-profile-mount-volume"
            },
            "hello": {
                "volume_profile": "xfs"  # hardcoded in `tools/create_testing_volumes.py`.
            }
        }

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, NUM_HELLO, additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.12")
def test_profile_mount_volumes():
    sdk_tasks.check_running(config.SERVICE_NAME, NUM_HELLO)
