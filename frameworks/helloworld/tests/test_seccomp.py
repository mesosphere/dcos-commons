import logging
import pytest
import json

import sdk_install
import sdk_agents
import sdk_cmd
import sdk_plan
import sdk_marathon

from tests import config

log = logging.getLogger(__name__)

SECCOMP_DIR = "/opt/mesosphere/etc/dcos/mesos/seccomp/test_profile.json"
custom_profile = {
    "defaultAction": "SCMP_ACT_ALLOW",
    "archMap": [
        {
            "architecture": "SCMP_ARCH_X86_64",
            "subArchitectures": [
                "SCMP_ARCH_X86",
                "SCMP_ARCH_X32"
            ]
        }
    ],
    "syscalls": [
        {
            "names": ["uname"],
            "action": "SCMP_ACT_ERRNO",
            "args": [],
            "includes": {},
            "excludes": {}
        }
    ]
}


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        # setup custom seccomp profile
        all_agent_ips = set([agent["hostname"] for agent in sdk_agents.get_agents()])
        for ip in all_agent_ips:
            print("Copying custom seccomp profile to agent %s" % ip)
            sdk_cmd.agent_scp(ip, json.dumps(custom_profile), SECCOMP_DIR, )

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {
            "service": {
                "yaml": "seccomp"
            },
            "hello": {
                "seccomp-profile-name": "default.json"
            }
        }

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, expected_running_tasks=1 , additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("1.13")
@pytest.mark.sanity
def test_custom_seccomp_profile():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)

    # uname will now be dissalowed and svc should crashloop
    marathon_config["env"]["HELLO_SECCOMP_PROFILE_NAME"] = "test_profile.json"
    sdk_marathon.update_app(marathon_config)
    sdk_marathon.wait_for_deployment(config.SERVICE_NAME, 60, None)
