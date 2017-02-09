import sdk_plan as plan
import sdk_tasks as tasks


PACKAGE_NAME = 'hdfs'
DEFAULT_TASK_COUNT = 10 # 3 data nodes, 3 journal nodes, 2 name nodes, 2 zkfc nodes


def check_healthy(count = DEFAULT_TASK_COUNT):
    # Getting a plan only returns 200 when it is complete,
    # so when getting the plan succeeds, the plan is also complete.
    plan.get_plan(PACKAGE_NAME, "deploy")
    plan.get_plan(PACKAGE_NAME, "recovery")
    tasks.check_running(PACKAGE_NAME, count)
