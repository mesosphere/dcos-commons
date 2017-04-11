#!/usr/bin/python3

# shakedown requires python3

import logging
import shakedown
import time


# Methods to modify the envvar settings of a mesos master and restart
# the master process after they are modified. Ideally, we would just get clusters
# that are preconfigured like this. But, this allows us to modify an existent cluster.
logger = logging.getLogger(__name__)


def set_master_envvar(envvar, value):
    as_dict = {envvar: value}
    modify_envvars(lambda envvars: envvars.update(as_dict))


def set_master_envvars(new_envvars_as_dict):
    modify_envvars(lambda envvars: envvars.update(new_envvars_as_dict))


def remove_master_envvar(envvar):
    modify_envvars(lambda envvars: envvars.pop(envvar, None))


def modify_envvars(modifier):
    logger.info("Modifying envvars on master node...")
    # Get the current set of master envvars
    success, out = shakedown.run_command_on_master('cat /opt/mesosphere/etc/mesos-master')
    if success is not True:
        logger.info('Unable to get current envvars from master: {}'.format(out))
        raise RuntimeError("Unable to get current envvars")

    logger.info("Current envvars:\n{}".format(out))
    envvars, commented = process_envvars(out)
    modifier(envvars)
    write_envvars(envvars, commented)
    restart_master()
    logger.info("Modification complete.")


def process_envvars(input):
    logger.info("Processing envvars...")
    lines = input.split('\n')

    envvars = {}
    commented = []
    for line in lines:
        if line.lstrip().startswith('#'):
            commented.append(line)
            continue
        if '=' not in line:
            continue
        var, val = line.split('=', 1)
        envvars[var] = val

    return envvars, commented


def write_envvars(envvars, commented_envvars):
    logger.info("Writing envvars...")
    content = []
    for evar in envvars:
        content.append('{}={}'.format(evar, envvars[evar]))

    for comment in commented_envvars:
        content.append(comment)

    new_file_content = '\n'.join(content)

    success, out = shakedown.run_command_on_master('sudo sh -c \'echo "{}" > /opt/mesosphere/etc/mesos-master\''.format(new_file_content))

    logger.info("Wrote new envvars:\n{}".format(out))

    if success is not True:
        raise RuntimeError("Unable to modify envvars on master")


def restart_master():
    logger.info("Restarting master process...")

    success, out = shakedown.run_command_on_master('sudo systemctl restart dcos-mesos-master && while true; do curl leader.mesos:5050; if [ $? == 0 ]; then break; fi; done')
    if success is not True:
        raise RuntimeError("Unable to restart master: {}".format(out))

    logger.info(out)


def set_local_infinity_defaults():
    remove_master_envvar('MESOS_SLAVE_REMOVAL_RATE_LIMIT')
    remove_master_envvar('MESOS_MAX_SLAVE_PING_TIMEOUTS')
    modified_envvars = {
        # During our testing, we remove agents. This requires us to 
        # increase the rate at which we can remove agents. The new value
        # of the rate limit indicates we can remove 100 agents every 20 minutes.
        'MESOS_AGENT_REMOVAL_RATE_LIMIT': '100/20mins',
        # During our testing, we remove agents. This requires us to lower the number of checks
        # that are done before removal when an agent is not responding so that removal
        # is quicker. The ping timeout (MESOS_AGENT_PING_TIMEOUT) is 15 seconds, and
        # thus we will wait 1 minute for agent death.
        'MESOS_MAX_AGENT_PING_TIMEOUTS': '4'
    }
    set_master_envvars(modified_envvars)


if __name__ == "__main__":
    set_local_infinity_defaults()
