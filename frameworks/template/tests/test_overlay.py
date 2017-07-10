import os
import pytest

import sdk_install as install
import sdk_utils as utils
import sdk_networks as networks

import shakedown


from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

overlay_nostrict = pytest.mark.skipif(os.environ.get("SECURITY") == "strict",
    reason="overlay tests currently broken in strict")

def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, additional_options=networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@overlay_nostrict
@utils.dcos_1_9_or_higher
def test_install():
    networks.check_task_network("template-0-node")
