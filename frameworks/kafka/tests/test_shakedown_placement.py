import pytest

import sdk_install as install
import sdk_tasks as tasks
import sdk_marathon as marathon
import sdk_package as package
import sdk_cmd as command
import sdk_plan as plan
import sdk_spin as spin
import sdk_utils as utils
import shakedown
import dcos


from tests.test_utils import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_BROKER_COUNT,
    service_cli,
    POD_TYPE
)


def setup_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)
    utils.gc_frameworks()


# --------- Placement -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_placement_unique_hostname():
    install.install(
        PACKAGE_NAME,
        DEFAULT_BROKER_COUNT,
        service_name=SERVICE_NAME,
        additional_options = {'service':{'placement_constraint':'hostname:UNIQUE'}}
    )
    # double check
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    pl = service_cli('plan show deploy')
    assert pl['status'] == 'COMPLETE'
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_placement_max_one_per_hostname():
    install.install(
        PACKAGE_NAME,
        DEFAULT_BROKER_COUNT,
        service_name=SERVICE_NAME,
        additional_options={'service':{'placement_constraint':'hostname:MAX_PER:1'}}
    )
    # double check
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    pl = service_cli('plan show deploy')
    assert pl['status'] == 'COMPLETE'
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_marathon_rack_not_found():
    def fun():
        try:
            return service_cli('plan show deploy')
        except:
            return False

    shakedown.install_package(PACKAGE_NAME,
                              service_name=SERVICE_NAME,
                              options_json=install.get_package_options(
                                  additional_options={'service':{'placement_constraint':'rack_id:LIKE:rack-foo-.*'}}
                              ),
                              wait_for_completion=False)
    try:
        tasks.check_running(PACKAGE_NAME, 1, timeout=120)
        assert False, "Should have failed to install"
    except:
        pass  # expected to fail

    pl = spin.time_wait_return(fun)

    # check that first node is still (unsuccessfully) looking for a match:
    assert pl['status'] == 'IN_PROGRESS'
    assert pl['phases'][0]['status'] == 'IN_PROGRESS'
    assert pl['phases'][0]['steps'][0]['status'] in ('PREPARED', 'PENDING')
    assert pl['phases'][0]['steps'][1]['status'] == 'PENDING'
    assert pl['phases'][0]['steps'][2]['status'] == 'PENDING'
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)

