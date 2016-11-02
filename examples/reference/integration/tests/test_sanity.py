import dcos.http
import inspect
import os
import pytest
import shakedown

from tests.test_utils import (
    DEFAULT_TASK_COUNT,
    PACKAGE_NAME,
    check_health,
    get_marathon_config,
    marathon_api_url,
    request,
    uninstall,
)


strict_mode = os.getenv('SECURITY', 'permissive')


def setup_module(module):
    uninstall()

    if strict_mode == 'strict':
        shakedown.install_package_and_wait(
            package_name=PACKAGE_NAME,
            options_file=os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe()))) + "/strict.json")
    else:
        shakedown.install_package_and_wait(
            package_name=PACKAGE_NAME,
            options_file=None)

    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
def test_install_worked():
    pass


@pytest.mark.sanity
def test_bump_metadata_cpus():
    config = get_marathon_config()
    cpus = int(config['env']['METADATA_CPU'])
    config['env']['METADATA_CPU'] = str(cpus + 0.1)
    r = request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    check_health()


@pytest.mark.sanity
def test_bump_data_nodes():
    config = get_marathon_config()
    dataNodes = int(config['env']['DATA_COUNT'])
    config['env']['DATA_COUNT'] = str(dataNodes + 1)
    r = request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    check_health(DEFAULT_TASK_COUNT + 1)
