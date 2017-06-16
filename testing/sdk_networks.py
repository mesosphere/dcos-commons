import shakedown


def check_task_network(task_name, expected_network_name="dcos"):
    """Tests whether a task (and it's parent pod) is on a given network
    """
    _task = shakedown.get_task(task_id=task_name, completed=False)
    for status in _task["statuses"]:
        if status["state"] == "TASK_RUNNING":
            for network_info in status["container_status"]["network_infos"]:
                if expected_network_name is not None:
                    assert "name" in network_info, \
                        "Didn't find network name in NetworkInfo for task {task} with " \
                        "status:{status}".format(task=task_name, status=status)
                    assert network_info["name"] == expected_network_name, \
                        "Expected network name:{expected} found:{observed}" \
                        .format(expected=expected_network_name, observed=network_info["name"])
                else:
                    assert "name" not in network_info, \
                        "Task {task} has network name when it shouldn't has status:{status}" \
                        .format(task=task_name, status=status)
