"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_utils IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import functools
import logging
import os
import os.path
import pytest
import random
import string

import sdk_cmd

from distutils.version import LooseVersion

log = logging.getLogger(__name__)


###
# Service/task names
###


def get_package_name(default: str) -> str:
    return os.environ.get("INTEGRATION_TEST__PACKAGE_NAME") or default


def get_service_name(default: str) -> str:
    return os.environ.get("INTEGRATION_TEST__SERVICE_NAME") or default


def get_foldered_name(service_name):
    # DCOS 1.9 & earlier don't support "foldered", service names aka marathon group names
    if dcos_version_less_than("1.10"):
        return service_name
    return "/test/integration/" + service_name


def get_task_id_service_name(service_name):
    """Converts the provided service name to a sanitized name as used in task ids.

    For example: /test/integration/foo => test.integration.foo"""
    return service_name.lstrip("/").replace("/", ".")


def get_task_id_prefix(service_name, task_name):
    """Returns the TaskID prefix to be used for the provided service name and task name.
    The full TaskID would consist of this prefix, plus two underscores and a UUID.

    For example: /test/integration/foo + hello-0-server => test.integration.foo__hello-0-server"""
    return "{}__{}".format(get_task_id_service_name(service_name), task_name)


def get_deslashed_service_name(service_name):
    # Foldered services have slashes removed: '/test/integration/foo' => 'test__integration__foo'.
    return service_name.lstrip("/").replace("/", "__")


def get_role(service_name):
    return "{}-role".format(get_deslashed_service_name(service_name))


def get_zk_path(service_name):
    return "dcos-service-{}".format(get_deslashed_service_name(service_name))


###
# DCOS version checks
###


@functools.lru_cache()
def dcos_url():
    _, stdout, _ = sdk_cmd.run_cli("config show core.dcos_url")
    return stdout.strip()


@functools.lru_cache()
def dcos_token():
    _, stdout, _ = sdk_cmd.run_cli("config show core.dcos_acs_token", print_output=False)
    return stdout.strip()


@functools.lru_cache()
def dcos_version():
    return sdk_cmd.cluster_request("GET", "/dcos-metadata/dcos-version.json").json()["version"]


@functools.lru_cache()
def dcos_version_less_than(version):
    cluster_version = dcos_version()
    index = version.rfind("-dev")
    if index != -1:
        cluster_version = version[:index]
    return LooseVersion(cluster_version) < LooseVersion(version)


def dcos_version_at_least(version):
    return not dcos_version_less_than(version)


def check_dcos_min_version_mark(item: pytest.Item):
    """Enforces the dcos_min_version pytest annotation, which should be used like this:

    @pytest.mark.dcos_min_version('1.10')
    def your_test_here(): ...

    In order for this annotation to take effect, this function must be called by a pytest_runtest_setup() hook.
    """
    min_version_mark = item.get_marker("dcos_min_version")
    if min_version_mark:
        min_version = min_version_mark.args[0]
        message = "Feature only supported in DC/OS {} and up".format(min_version)
        if "reason" in min_version_mark.kwargs:
            message += ": {}".format(min_version_mark.kwargs["reason"])
        if dcos_version_less_than(min_version):
            pytest.skip(message)


def is_open_dcos():
    """Determine if the tests are being run against open DC/OS. This is presently done by
    checking the envvar DCOS_ENTERPRISE."""
    return not (os.environ.get("DCOS_ENTERPRISE", "true").lower() == "true")


def is_strict_mode():
    """Determine if the tests are being run on a strict mode cluster."""
    return os.environ.get("SECURITY", "") == "strict"


"""Annotation which may be used to mark test suites or test cases as EE-only.

Suite:
> pytestmark = sdk_utils.dcos_ee_only
or
> pytestmark = [othercheck, sdk_utils.dcos_ee_only]

Test:
> @sdk_utils.dcos_ee_only  # at top of test
"""
dcos_ee_only = pytest.mark.skipif(is_open_dcos(), reason="Feature only supported in DC/OS EE.")


###
# Misc data manipulation
###


def pretty_duration(seconds):
    """ Returns a user-friendly representation of the provided duration in seconds.
    For example: 62.8 => "1m2.8s", or 129837.8 => "2d12h4m57.8s"
    """
    if seconds is None:
        return ""
    ret = ""
    if seconds >= 86400:
        ret += "{:.0f}d".format(int(seconds / 86400))
        seconds = seconds % 86400
    if seconds >= 3600:
        ret += "{:.0f}h".format(int(seconds / 3600))
        seconds = seconds % 3600
    if seconds >= 60:
        ret += "{:.0f}m".format(int(seconds / 60))
        seconds = seconds % 60
    if len(ret) == 0:
        # nothing in duration string yet: be more accurate
        ret += "{:.3f}s".format(seconds)
    else:
        ret += "{:.1f}s".format(seconds)
    return ret


def random_string(length=8):
    return "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(length))


def merge_dictionaries(dict1, dict2):
    if not isinstance(dict2, dict):
        return dict1
    ret = {}
    for k, v in dict1.items():
        ret[k] = v
    for k, v in dict2.items():
        if k in dict1 and isinstance(dict1[k], dict) and isinstance(dict2[k], collections.Mapping):
            ret[k] = merge_dictionaries(dict1[k], dict2[k])
        else:
            ret[k] = dict2[k]
    return ret
