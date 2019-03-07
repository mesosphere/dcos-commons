import logging
import pytest
import retrying
from typing import Any, Dict, Iterator, List

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks


from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {
            "service": {
                # empty yaml: start in dynamic multiservice mode
                "yaml": ""
            }
        }

        # do not poll scheduler-level deploy plan, there is none:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            0,
            additional_options=options,
            wait_for_deployment=False,
        )

        # use yaml list as a proxy for checking that the scheduler is up:
        yamls = sdk_cmd.service_request("GET", config.SERVICE_NAME, "/v1/multi/yaml").json()
        assert "svc" in yamls

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_add_deploy_restart_remove() -> None:
    svc1 = "test1"

    # add svc as test1:
    sdk_cmd.service_request(
        "POST", config.SERVICE_NAME, "/v1/multi/{}?yaml=svc".format(svc1), json=service_params(svc1)
    )
    # get list, should immediately have new entry:
    service = get_service_list()[0]
    assert service["service"] == svc1
    assert service["yaml"] == "svc"
    assert not service["uninstall"]

    sdk_plan.wait_for_plan_status(config.SERVICE_NAME, "deploy", "COMPLETE", multiservice_name=svc1)

    task_ids = sdk_tasks.get_task_ids("marathon", config.SERVICE_NAME)
    log.info("list of task ids {}".format(task_ids))
    old_task_id = task_ids[0]

    # restart and check that service is recovered:
    sdk_marathon.restart_app(config.SERVICE_NAME)

    # check that scheduler task was relaunched
    sdk_tasks.check_scheduler_relaunched(config.SERVICE_NAME, old_task_id)

    service = wait_for_service_count(1)[0]
    assert service["service"] == svc1
    assert service["yaml"] == "svc"
    assert not service["uninstall"]

    plan = sdk_plan.wait_for_plan_status(
        config.SERVICE_NAME, "deploy", "COMPLETE", multiservice_name=svc1
    )
    # verify that svc.yml was deployed as svc1:
    assert sdk_plan.get_all_step_names(plan) == [
        "hello-0:[server]",
        "world-0:[server]",
        "world-1:[server]",
    ]

    # trigger service removal, wait for removal:
    sdk_cmd.service_request("DELETE", config.SERVICE_NAME, "/v1/multi/{}".format(svc1))
    # check delete bit is set. however, be permissive of service being removed VERY quickly:
    services = get_service_list()
    assert len(services) <= 1
    for service in services:
        assert service["service"] == svc1
        assert service["yaml"] == "svc"
        assert service["uninstall"]
    wait_for_service_count(0)


@pytest.mark.sanity
def test_add_multiple_uninstall() -> None:
    # add two services:
    svc1 = "test1"
    sdk_cmd.service_request(
        "POST", config.SERVICE_NAME, "/v1/multi/{}?yaml=svc".format(svc1), json=service_params(svc1)
    )
    svc2 = "test2"
    sdk_cmd.service_request(
        "POST",
        config.SERVICE_NAME,
        "/v1/multi/{}?yaml=simple".format(svc2),
        json=service_params(svc2),
    )

    # get list, should immediately have new entries:
    services = get_service_list()
    assert len(services) == 2
    for service in services:
        name = service["service"]
        assert name in (svc1, svc2)
        if name == svc1:
            assert service["yaml"] == "svc"
        else:
            assert service["yaml"] == "simple"
        assert not service["uninstall"]

    plan = sdk_plan.wait_for_plan_status(
        config.SERVICE_NAME, "deploy", "COMPLETE", multiservice_name=svc1
    )
    # verify that svc.yml was deployed as svc1:
    assert sdk_plan.get_all_step_names(plan) == [
        "hello-0:[server]",
        "world-0:[server]",
        "world-1:[server]",
    ]

    plan = sdk_plan.wait_for_plan_status(
        config.SERVICE_NAME, "deploy", "COMPLETE", multiservice_name=svc2
    )
    # verify that simple.yml was deployed as svc2:
    assert sdk_plan.get_all_step_names(plan) == ["hello-0:[server]"]

    # remove one service, then immediately restart app to verify recovery during service removal:
    sdk_cmd.service_request("DELETE", config.SERVICE_NAME, "/v1/multi/{}".format(svc2))
    # check delete bits is set. however, be permissive of service potentially being removed VERY quickly:
    services = get_service_list()
    assert len(services) in (1, 2)
    for service in services:
        name = service["service"]
        assert name in (svc1, svc2)
        if name == svc1:
            assert service["yaml"] == "svc"
        else:
            assert service["yaml"] == "simple"
        # svc2 should be getting uninstalled, svc1 shouldn't:
        assert service["uninstall"] == (name == svc2)

    # restart app and wait for removal to succeed after restart:
    sdk_marathon.restart_app(config.SERVICE_NAME)
    wait_for_service_count(1)

    plan = sdk_plan.wait_for_plan_status(
        config.SERVICE_NAME, "deploy", "COMPLETE", multiservice_name=svc1
    )
    # verify that svc.yml is still deployed as svc1:
    assert sdk_plan.get_all_step_names(plan) == [
        "hello-0:[server]",
        "world-0:[server]",
        "world-1:[server]",
    ]

    # leave suite teardown to do the uninstall, verifying successful winding down of svc1


def get_service_list() -> List[Dict[str, Any]]:
    response = sdk_cmd.service_request("GET", config.SERVICE_NAME, "/v1/multi")
    service_list = response.json()
    assert isinstance(service_list, list)
    return service_list


@retrying.retry(wait_fixed=1000, stop_max_delay=5 * 60 * 1000)
def wait_for_service_count(count: int):
    services = get_service_list()
    log.info(
        "Waiting for scheduler to have {} services, got {}: {}".format(
            count, len(services), services
        )
    )
    if len(services) is not count:
        raise Exception("Expected {} services, got: {}".format(count, services))
    return services


def service_params(service_name: str) -> Dict[str, str]:
    # we just override the service name in the YAML, otherwise we use the scheduler env:
    return {"FRAMEWORK_NAME": service_name}
