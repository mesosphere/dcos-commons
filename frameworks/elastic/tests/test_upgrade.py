import logging

import pytest

import sdk_install
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
expected_task_count = config.DEFAULT_TASK_COUNT


@pytest.fixture(scope="module", autouse=True)
def set_up_security(configure_security):
    yield


@pytest.fixture(autouse=True)
def uninstall_packages(configure_security):
    try:
        log.info("Ensuring Elastic and Kibana are uninstalled before running test")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        yield  # let the test session execute
    finally:
        log.info("Ensuring Elastic and Kibana are uninstalled after running test")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


# TODO(mpereira): it is safe to remove this test after the 6.x release.
@pytest.mark.sanity
@pytest.mark.timeout(20 * 60)
def test_xpack_update_matrix():
    # Updating from X-Pack 'enabled' to X-Pack security 'enabled' (the default) is more involved
    # than the other cases, so we use `test_upgrade_from_xpack_enabled`.
    log.info("Updating X-Pack from 'enabled' to 'enabled'")
    config.test_upgrade_from_xpack_enabled(
        config.PACKAGE_NAME,
        foldered_name,
        {"elasticsearch": {"xpack_enabled": True}},
        expected_task_count,
    )

    log.info("Updating X-Pack from 'enabled' to 'disabled'")
    config.test_xpack_enabled_update(foldered_name, True, False)

    log.info("Updating X-Pack from 'disabled' to 'enabled'")
    config.test_xpack_enabled_update(foldered_name, False, True)

    log.info("Updating X-Pack from 'disabled' to 'disabled'")
    config.test_xpack_enabled_update(foldered_name, False, False)


# TODO(mpereira): change this to xpack_security_enabled to xpack_security_enabled after the 6.x
# release.
@pytest.mark.sanity
@pytest.mark.timeout(30 * 60)
def test_xpack_security_enabled_update_matrix():
    # Updating from X-Pack 'enabled' to X-Pack security 'enabled' is more involved than the other
    # cases, so we use `test_upgrade_from_xpack_enabled`.
    log.info("Updating from X-Pack 'enabled' to X-Pack security 'enabled'")
    config.test_upgrade_from_xpack_enabled(
        config.PACKAGE_NAME,
        foldered_name,
        {"elasticsearch": {"xpack_security_enabled": True}},
        expected_task_count,
    )

    log.info("Updating from X-Pack 'enabled' to X-Pack security 'disabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, True, False)

    log.info("Updating from X-Pack 'disabled' to X-Pack security 'enabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, False, True)

    log.info("Updating from X-Pack 'disabled' to X-Pack security 'disabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, False, False)
