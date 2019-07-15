import logging
import pytest

import sdk_install
import sdk_plan


from tests import config

log = logging.getLogger(__name__)

@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {
            "service": {
                "yaml": "shm"
            }
        }

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, expected_running_tasks=1 , additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_shm():
    # Launch a top-level container with `PRIVATE` IPC mode and 128MB /dev/shm.
    # Launch the first child container, check its /dev/shm size is 128MB
    # rather than 256MB, It is in the same IPC namespace with its parent container,
    # and then write its IPC namespace inode to a file under /dev/shm.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
