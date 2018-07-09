import logging
import typing

import sdk_cmd
import sdk_plan
import sdk_tasks


LOG = logging.getLogger(__name__)


def check_permanent_recovery(
    package_name: str,
    service_name: str,
    pod_name: str,
    recovery_timeout_s: int,
    pods_with_updated_tasks: typing.List[str] = None,
):
    """
    Perform a replace operation on a specified pod and check that it is replaced

    All other pods are checked to see if they remain consistent.
    """
    LOG.info("Testing pod replace operation for %s:%s", service_name, pod_name)

    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_plan.wait_for_completed_recovery(service_name)

    pod_list = set(sdk_cmd.svc_cli(package_name, service_name, "pod list", json=True))

    pods_to_update = set(
        pods_with_updated_tasks if pods_with_updated_tasks else [] + [pod_name]
    )

    tasks_to_replace = {}
    for pod in pods_to_update:
        tasks_to_replace[pod] = set(sdk_tasks.get_task_ids(service_name, pod_name))

    LOG.info("The following tasks will be replaced: %s", tasks_to_replace)

    tasks_in_other_pods = {}
    for pod in pod_list - pods_to_update:
        tasks_in_other_pods[pod] = set(sdk_tasks.get_task_ids(service_name, pod))

    LOG.info("Tasks in other pods should not be replaced: %s", tasks_in_other_pods)

    replace_cmd = ["pod", "replace", pod_name]
    sdk_cmd.svc_cli(package_name, service_name, " ".join(replace_cmd), json=True)

    sdk_plan.wait_for_kicked_off_recovery(service_name, recovery_timeout_s)
    sdk_plan.wait_for_completed_recovery(service_name, recovery_timeout_s)

    for pod, tasks in tasks_to_replace.items():
        sdk_tasks.check_tasks_updated(service_name, pod, tasks)

    for pod, tasks in tasks_in_other_pods.items():
        sdk_tasks.check_tasks_not_updated(service_name, pod, tasks)
