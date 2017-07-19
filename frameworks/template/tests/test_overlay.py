import os
import pytest

import sdk_install
import sdk_utils
import sdk_networks

import shakedown


from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

overlay_nostrict = pytest.mark.skipif(os.environ.get("SECURITY") == "strict",
    reason="overlay tests currently broken in strict")

<<<<<<< HEAD
@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(PACKAGE_NAME)
        utils.gc_frameworks()
        install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, additional_options=networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)

        yield # let the test session execute
    finally:
        install.uninstall(PACKAGE_NAME)
=======
def setup_module(module):
    sdk_install.uninstall(PACKAGE_NAME)
    sdk_utils.gc_frameworks()
    sdk_install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)


def teardown_module(module):
    sdk_install.uninstall(PACKAGE_NAME)
>>>>>>> master


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@overlay_nostrict
@sdk_utils.dcos_1_9_or_higher
def test_install():
    sdk_networks.check_task_network("template-0-node")
