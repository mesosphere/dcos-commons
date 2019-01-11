import logging

import pytest

import sdk_install
import sdk_repository
import sdk_service
import sdk_upgrade
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
current_expected_task_count = config.DEFAULT_TASK_COUNT


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        log.info("Ensure elasticsearch and kibana are uninstalled...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        yield  # let the test session execute
    finally:
        log.info("Clean up elasticsearch and kibana...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


# TODO(mpereira): it is safe to remove this test after the 6.x release.
@pytest.mark.sanity
@pytest.mark.timeout(20 * 60)
def test_xpack_update_matrix():
    # Since this test uninstalls the Elastic service that is shared between all previous tests,
    # reset the number of expected tasks to the default value. This is checked before all tests
    # by the `pre_test_setup` fixture.
    global current_expected_task_count
    current_expected_task_count = config.DEFAULT_TASK_COUNT

    log.info("Updating X-Pack from 'enabled' to 'enabled'")
    config.test_xpack_enabled_update(foldered_name, True, True)

    log.info("Updating X-Pack from 'enabled' to 'disabled'")
    config.test_xpack_enabled_update(foldered_name, True, False)

    log.info("Updating X-Pack from 'disabled' to 'enabled'")
    config.test_xpack_enabled_update(foldered_name, False, True)

    log.info("Updating X-Pack from 'disabled' to 'disabled'")
    config.test_xpack_enabled_update(foldered_name, False, False)


@pytest.mark.sanity
@pytest.mark.timeout(15 * 60)
def test_upgrade_from_xpack_enabled_to_xpack_security_enabled():
    # Since this test uninstalls the Elastic service that is shared between all previous tests,
    # reset the number of expected tasks to the default value. This is checked before all tests
    # by the `pre_test_setup` fixture.
    global current_expected_task_count
    current_expected_task_count = config.DEFAULT_TASK_COUNT

    # This test needs to run some code in between the Universe version installation and the stub Universe
    # upgrade, so it cannot use `sdk_upgrade.test_upgrade`.
    log.info("Updating from X-Pack 'enabled' to X-Pack security 'enabled'")
    http_user = config.DEFAULT_ELASTICSEARCH_USER
    http_password = config.DEFAULT_ELASTICSEARCH_PASSWORD
    package_name = config.PACKAGE_NAME

    sdk_install.uninstall(package_name, foldered_name)

    # Move Universe repo to the top of the repo list so that we can first install the Universe
    # version.
    _, universe_version = sdk_repository.move_universe_repo(package_name, universe_repo_index=0)

    sdk_install.install(
        package_name,
        foldered_name,
        expected_running_tasks=current_expected_task_count,
        additional_options={"elasticsearch": {"xpack_enabled": True}},
        package_version=universe_version,
    )

    document_es_5_id = 1
    document_es_5_fields = {"name": "Elasticsearch 5: X-Pack enabled", "role": "search engine"}
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_es_5_id,
        document_es_5_fields,
        service_name=foldered_name,
        http_user=http_user,
        http_password=http_password,
    )

    # This is the first crucial step when upgrading from "X-Pack enabled" on ES5 to "X-Pack security
    # enabled" on ES6. The default "changeme" password doesn't work anymore on ES6, so passwords
    # *must* be *explicitly* set, otherwise nodes won't authenticate requests, leaving the cluster
    # unavailable. Users will have to do this manually when upgrading.
    config._curl_query(
        foldered_name,
        "POST",
        "_xpack/security/user/{}/_password".format(http_user),
        json_body={"password": http_password},
        http_user=http_user,
        http_password=http_password,
    )

    # Move Universe repo back to the bottom of the repo list so that we can upgrade to the version
    # under test.
    _, test_version = sdk_repository.move_universe_repo(package_name)

    # First we upgrade to "X-Pack security enabled" set to false on ES6, so that we can use the
    # X-Pack migration assistance and upgrade APIs.
    sdk_upgrade.update_or_upgrade_or_downgrade(
        package_name,
        foldered_name,
        test_version,
        {
            "service": {"update_strategy": "parallel"},
            "elasticsearch": {"xpack_security_enabled": False},
        },
        current_expected_task_count,
    )

    # Get list of indices to upgrade from here. The response looks something like:
    # {
    #   "indices" : {
    #     ".security" : {
    #       "action_required" : "upgrade"
    #     },
    #     ".watches" : {
    #       "action_required" : "upgrade"
    #     }
    #   }
    # }
    response = config._curl_query(foldered_name, "GET", "_xpack/migration/assistance?pretty")

    # This is the second crucial step when upgrading from "X-Pack enabled" on ES5 to "X-Pack
    # security enabled" on ES6. The ".security" index (along with any others returned by the
    # "assistance" API) needs to be upgraded.
    for index in response["indices"]:
        config._curl_query(
            foldered_name,
            "POST",
            "_xpack/migration/upgrade/{}?pretty".format(index),
            http_user=http_user,
            http_password=http_password,
        )

    document_es_6_security_disabled_id = 2
    document_es_6_security_disabled_fields = {
        "name": "Elasticsearch 6: X-Pack security disabled",
        "role": "search engine",
    }
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_es_6_security_disabled_id,
        document_es_6_security_disabled_fields,
        service_name=foldered_name,
        http_user=http_user,
        http_password=http_password,
    )

    # After upgrading the indices, we're now safe to enable X-Pack security.
    sdk_service.update_configuration(
        package_name,
        foldered_name,
        {"elasticsearch": {"xpack_security_enabled": True}},
        current_expected_task_count,
    )

    document_es_6_security_enabled_id = 3
    document_es_6_security_enabled_fields = {
        "name": "Elasticsearch 6: X-Pack security enabled",
        "role": "search engine",
    }
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_es_6_security_enabled_id,
        document_es_6_security_enabled_fields,
        service_name=foldered_name,
        http_user=http_user,
        http_password=http_password,
    )

    # Make sure that documents were created and are accessible.
    config.verify_document(
        foldered_name,
        document_es_5_id,
        document_es_5_fields,
        http_user=http_user,
        http_password=http_password,
    )
    config.verify_document(
        foldered_name,
        document_es_6_security_disabled_id,
        document_es_6_security_disabled_fields,
        http_user=http_user,
        http_password=http_password,
    )
    config.verify_document(
        foldered_name,
        document_es_6_security_enabled_id,
        document_es_6_security_enabled_fields,
        http_user=http_user,
        http_password=http_password,
    )


# TODO(mpereira): change this to xpack_security_enabled to xpack_security_enabled after the 6.x
# release.
@pytest.mark.sanity
@pytest.mark.timeout(30 * 60)
def test_xpack_security_enabled_update_matrix():
    # Since this test uninstalls the Elastic service that is shared between all previous tests,
    # reset the number of expected tasks to the default value. This is checked before all tests
    # by the `pre_test_setup` fixture.
    global current_expected_task_count
    current_expected_task_count = config.DEFAULT_TASK_COUNT

    # Updating from X-Pack 'enabled' to X-Pack security 'disabled' is more complex than the other
    # cases, so it's done separately in the previous test.

    log.info("Updating from X-Pack 'enabled' to X-Pack security 'disabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, True, False)

    log.info("Updating from X-Pack 'disabled' to X-Pack security 'enabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, False, True)

    log.info("Updating from X-Pack 'disabled' to X-Pack security 'disabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, False, False)
