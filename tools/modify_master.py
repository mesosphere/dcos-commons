#!/usr/bin/python

import shakedown
import logging


# Methods to modify the envvar settings of a mesos master and restart
# the master process after they are modified

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
        if '#' in line:
            commented.append(line)
            continue
        if '=' not in line:
            continue
        bits = line.split('=')
        if len(bits) != 2:
            raise ValueError('Envvar file had badly formatted line: {}\nfile: {}'.format(
                line, input))
        
        envvars[bits[0]] = bits[1]

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

    success, out = shakedown.run_command_on_master('sudo systemctl condrestart dcos-mesos-master')
    if success is not True:
        print("wtf...")
        raise RuntimeError("Unable to restart master")


# Note: This is a hack for now. Talk to bwood about it.
set_master_envvar('MESOS_SLAVE_REMOVAL_RATE_LIMIT', '100/20mins')