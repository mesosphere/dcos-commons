from tests import config
import pytest
import sdk_cmd
import sdk_install
import sdk_tasks
import logging
log = logging.getLogger(__name__)


@pytest.mark.sanity
def test_xmx_and_xms_flags():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    #installing elastic service and passing customized json to overwrite default values.
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT
    )
   #getting all the tasks and checking the flag duplicacy by running curl_cmd command.
    for task in sdk_tasks.get_task_ids(config.SERVICE_NAME):
        curl_cmd = "ps aux"
        log.info(cmd)
        flag_xms = "Xms"
        flag_xmx = "Xmx"
        exit_code, stdout, stderr = sdk_cmd.service_task_exec(config.SERVICE_NAME,task,cmd)
        assert str(stdout).count(flag_xms) == 1, "Default flag prefix should appear once"
        assert str(stdout).count(flag_xmx) == 1, "Default flag prefix should appear once"
    #uninstalling the installed service
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
