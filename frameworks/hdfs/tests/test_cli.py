import logging

import pytest
import sdk_cmd
import sdk_install


from tests import config

log = logging.getLogger(__name__)

foldered_name = config.FOLDERED_SERVICE_NAME


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            timeout_seconds=30 * 60,
        )

        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_cli():
    sdk_cmd.run_cli("package install {} --yes --cli".format(config.PACKAGE_NAME))
    dirpath = "/test"
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "hdfs dfs -mkdir {}".format(dirpath))
    _, stdout, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "hdfs dfs -ls /")

    output = "Found 1 items"
    assert output in stdout
    assert dirpath in stdout
