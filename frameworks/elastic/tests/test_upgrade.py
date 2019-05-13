import logging
from typing import Iterator

import pytest

import sdk_install
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
expected_task_count = config.DEFAULT_TASK_COUNT


@pytest.fixture(scope="module", autouse=True)
def set_up_security(configure_security: None) -> Iterator[None]:
    yield


@pytest.fixture(autouse=True)
def uninstall_packages(configure_security: None) -> Iterator[None]:
    try:
        log.info("Ensuring Elastic and Kibana are uninstalled before running test")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        yield  # let the test session execute
    finally:
        log.info("Ensuring Elastic and Kibana are uninstalled after running test")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
@pytest.mark.timeout(30 * 60)
def test_xpack_enabled_update_matrix() -> None:
    from_version = "2.4.0-5.6.9"
    to_version = "2.5.0-6.3.2"

    # Updating from X-Pack 'enabled' to X-Pack Security 'enabled' is more involved than the other
    # cases, so we use `test_upgrade_from_xpack_enabled`.
    log.info("Updating X-Pack from 'enabled' to 'enabled'")
    config.test_upgrade_from_xpack_enabled(
        config.PACKAGE_NAME,
        foldered_name,
        {"elasticsearch": {"xpack_enabled": True}},
        expected_task_count,
        from_version=from_version,
        to_version=to_version,
    )

    log.info("Updating X-Pack from 'enabled' to 'disabled'")
    config.test_xpack_enabled_update(foldered_name, True, False, from_version, to_version)

    log.info("Updating X-Pack from 'disabled' to 'enabled'")
    config.test_xpack_enabled_update(foldered_name, False, True, from_version, to_version)

    log.info("Updating X-Pack from 'disabled' to 'disabled'")
    config.test_xpack_enabled_update(foldered_name, False, False, from_version, to_version)


@pytest.mark.sanity
@pytest.mark.timeout(30 * 60)
def test_xpack_enabled_to_xpack_security_enabled_update_matrix() -> None:
    from_version = "2.4.0-5.6.9"
    to_version = "2.5.0-6.3.2"

    # Updating from X-Pack 'enabled' to X-Pack Security 'enabled' (the default) is more involved
    # than the other cases, so we use `test_upgrade_from_xpack_enabled`.
    log.info("Updating X-Pack from 'enabled' to X-Pack Security 'enabled'")
    config.test_upgrade_from_xpack_enabled(
        config.PACKAGE_NAME,
        foldered_name,
        {"elasticsearch": {"xpack_security_enabled": True}},
        expected_task_count,
        from_version=from_version,
        to_version=to_version,
    )

    log.info("Updating from X-Pack to 'enabled' to X-Pack Security 'disabled'")
    config.test_xpack_enabled_update(foldered_name, True, False, from_version, to_version)

    log.info("Updating from X-Pack to 'disabled' to X-Pack Security 'enabled'")
    config.test_xpack_enabled_update(foldered_name, False, True, from_version, to_version)

    log.info("Updating from X-Pack to 'disabled' to X-Pack Security 'disabled'")
    config.test_xpack_enabled_update(foldered_name, False, False, from_version, to_version)


@pytest.mark.sanity
@pytest.mark.timeout(30 * 60)
def test_xpack_security_enabled_update_matrix() -> None:
    log.info("Updating X-Pack Security from 'enabled' to 'enabled'")
    config.test_xpack_security_enabled_update(foldered_name, True, True)

    log.info("Updating X-Pack Security from 'enabled' to 'disabled'")
    config.test_xpack_security_enabled_update(foldered_name, True, False)

    log.info("Updating X-Pack Security from 'disabled' to 'enabled'")
    config.test_xpack_security_enabled_update(foldered_name, False, True)

    log.info("Updating X-Pack Security from 'disabled' to 'disabled'")
    config.test_xpack_security_enabled_update(foldered_name, False, False)
