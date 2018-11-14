import json
import logging
import typing

from sdk.testing import sdk_cmd
from sdk.testing import sdk_plan
from sdk.testing import sdk_tasks


LOG = logging.getLogger(__name__)


def check_permanent_recovery(
    package_name: str,
    service_name: str,
    pod_name: str,
    recovery_timeout_s: int,
    pods_with_updated_tasks: typing.List[str] = None,
):
    """
    Perform a replace (permanent recovery) operation on the specified pod.

    The specified pod AND any additional pods in `pods_with_updated_tasks` are
    checked to ensure that their tasks have been restarted.

    Any remaining pods are checked to ensure that their tasks are not changed.

    For example, performing a pod replace kafka-0 on a Kafka framework should
    result in ONLY the kafa-0-broker task being restarted. In this case,
    pods_with_updated_tasks is specified as None.

    When performing a pod replace operation on a Cassandra seed node (node-0),
    a rolling restart of other nodes is triggered, and
    pods_with_updated_tasks = ["node-0", "node-1", "node-2"]
    (assuming a three node Cassandra ring)
    """
    LOG.info("Testing pod replace operation for %s:%s", service_name, pod_name)

    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_plan.wait_for_completed_recovery(service_name)

    rc, stdout, _ = sdk_cmd.svc_cli(package_name, service_name, "pod list")
    assert rc == 0, "Pod list failed"
    pod_list = set(json.loads(stdout))

    pods_to_update = set(pods_with_updated_tasks if pods_with_updated_tasks else [] + [pod_name])

    tasks_to_replace = {}
    for pod in pods_to_update:
        tasks_to_replace[pod] = set(sdk_tasks.get_task_ids(service_name, pod_name))

    LOG.info("The following tasks will be replaced: %s", tasks_to_replace)

    tasks_in_other_pods = {}
    for pod in pod_list - pods_to_update:
        tasks_in_other_pods[pod] = set(sdk_tasks.get_task_ids(service_name, pod))

    LOG.info("Tasks in other pods should not be replaced: %s", tasks_in_other_pods)

    sdk_cmd.svc_cli(package_name, service_name, "pod replace {}".format(pod_name))

    sdk_plan.wait_for_kicked_off_recovery(service_name, recovery_timeout_s)
    sdk_plan.wait_for_completed_recovery(service_name, recovery_timeout_s)

    for pod, tasks in tasks_to_replace.items():
        sdk_tasks.check_tasks_updated(service_name, pod, tasks)

    for pod, tasks in tasks_in_other_pods.items():
        sdk_tasks.check_tasks_not_updated(service_name, pod, tasks)
