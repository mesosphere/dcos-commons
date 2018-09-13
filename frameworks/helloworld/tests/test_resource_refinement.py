import pytest
import retrying
import shakedown

import sdk_install
import sdk_utils
import sdk_marathon

from tests import config


pytestmark = pytest.mark.skipif(
    sdk_utils.is_strict_mode() and sdk_utils.dcos_version_less_than('1.11'),
    reason="secure hierarchical roles are only supported on 1.11+")

pre_reserved_options = {
    "service": {
        "yaml": "pre-reserved"
    }
}


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        sdk_install.install(config.PACKAGE_NAME,
                            config.SERVICE_NAME,
                            config.DEFAULT_TASK_COUNT,
                            additional_options=pre_reserved_options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.dcos_min_version('1.10')
def test_install():
    config.check_running(config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.dcos_min_version('1.10')
def test_marathon_volume_collission():
    # This test validates that a service registered in a sub-role of
    # slave_public will _not_ unreserve Marathon volumes RESERVED
    # in the `slave_public` role.

    # Uninstall HW first
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    # Install the marathon app
    marathon_app_name = "persistent-test"
    persistent_app = {
        "id": marathon_app_name,
        "mem": 128,
        "user": "nobody",
        "cmd": "echo 'this is a test' > persistent-volume/test && sleep 10000",
        "container": {
            "type": "MESOS",
            "volumes": [
                {
                    "persistent": {
                        "type": "root",
                        "size": 500,
                        "constraints": []
                    },
                    "mode": "RW",
                    "containerPath": "persistent-volume"
                }
            ]
        }
    }
    try:
        sdk_marathon.install_app(persistent_app)

        # Get its persistent Volume
        host = sdk_marathon.get_scheduler_host(marathon_app_name)
        ok, pv_name = shakedown.run_command_on_agent(
            host, "ls /var/lib/mesos/slave/volumes/roles/slave_public")
        assert ok

        pv_name = pv_name.strip()

        @retrying.retry(
            wait_fixed=1000,
            stop_max_delay=60 * 1000
        )
        def check_content():
            ok, pv_content = shakedown.run_command_on_agent(
                host, "cat /var/lib/mesos/slave/volumes/roles/slave_public/{}/test".format(pv_name))
            assert pv_content.strip() == "this is a test"

        check_content()

        # Scale down the Marathon app
        app_config = sdk_marathon.get_config(marathon_app_name)
        app_config['instances'] = 0
        sdk_marathon.update_app(marathon_app_name, app_config)

        # Install Hello World
        sdk_install.install(config.PACKAGE_NAME,
                            config.SERVICE_NAME,
                            config.DEFAULT_TASK_COUNT,
                            additional_options=pre_reserved_options)

        # Make sure the persistent volume is still there
        check_content()

        # Uninstall Hello World
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        # Make sure the persistent volume is still there
        check_content()

        # Scale back up the marathon app
        app_config = sdk_marathon.get_config(marathon_app_name)
        app_config['instances'] = 1
        sdk_marathon.update_app(marathon_app_name, app_config)

        # Make sure the persistent volume is still there
        check_content()

    finally:
        # Reinstall hello world
        sdk_install.install(config.PACKAGE_NAME,
                            config.SERVICE_NAME,
                            config.DEFAULT_TASK_COUNT,
                            additional_options=pre_reserved_options)

        sdk_marathon.destroy_app(marathon_app_name)
