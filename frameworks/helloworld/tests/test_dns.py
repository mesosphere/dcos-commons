import dcos
import pytest
import shakedown

import sdk_install as install
import sdk_plan as plan
import sdk_spin as spin

from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/dns.yml"
        }
    }

    # this yml has 2 hello's + 0 world's:
    install.install(PACKAGE_NAME, 2, additional_options=options)


@pytest.mark.sanity
def test_discovery_is_set():
    pod_info = dcos.http.get(
        shakedown.dcos_service_url(PACKAGE_NAME) +
        "/v1/pods/{}/info".format("hello-0")).json()

    assert(all(p["info"]["discovery"]["name"] == "hello" for p in pod_info))
