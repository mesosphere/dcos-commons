import json
import logging

import pytest
import sdk_repository
import sdk_security
import sdk_utils
import shakedown

log = logging.getLogger(__name__)


@pytest.fixture(scope='session')
def configure_universe():
    yield from sdk_repository.universe_session()

@pytest.fixture(scope='session')
def configure_security(configure_universe):
    yield from sdk_security.security_session('cassandra')

@pytest.fixture(autouse=True)
def get_cassandra_plans_on_failure(request):
    yield
    if sdk_utils.is_test_failure(request):
        plans, err, rc = shakedown.run_dcos_command('cassandra plan list')
        if rc:
            log.error('Unable to fetch cassandra plan list: {}'.format(err))
            return
        planlist = json.loads(plans)
        for plan_name in planlist:
            plan, err, rc = shakedown.run_dcos_command('cassandra plan show {}'.format(plan_name))
            if rc:
                log.error('Unable to fetch cassandra plan for {}: {}'.format(plan_name, err))
                continue
            plan_filename = '{}_{}_plan.log'.format(request.node.name, plan_name)
            with open(plan_filename, 'w') as f:
                f.write(plan)
