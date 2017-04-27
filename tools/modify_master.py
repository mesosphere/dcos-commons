#!/usr/bin/env python3

""" Interface to reconfigure files on a mesos master as referenced by the
current dcos command line.

Usage:

with MesosMasterModifier() as mmm:
    mmm.set_master_envvar('ABC')
    mmm.remove_master_envvar('XYZ')

The settings take effect at the conclusion of the block, when mmm leaves
scope.
"""



# shakedown requires python3

import logging
import shakedown
import time


# Methods to modify the envvar settings of a mesos master and restart
# the master process after they are modified. Ideally, we would just get clusters
# that are preconfigured like this. But, this allows us to modify an existent cluster.
logger = logging.getLogger(__name__)


class MesosMasterModifier():
    """Proxies the idea of changing configuration on a mesos master.

    Implemented as a context manager largely because that's how things
    ought to work; we ought to choose a master, or accept the default for a
    cluster, but for now shakedown doesn't work that way.
    """

    def __init__(self):
        # Counterintuitively, there's no master-related state here.  shakedown
        # doesn't permit us to know who we're talking to
        self.master_envvars = {}
        self.commented_lines = []

    def __enter__(self):
        #Get the envvars at the start
        self._fetch_current_envvars()
        return self

    def __exit__(self, *stuff):
        _ = stuff
        self._write_envvars()
        self.restart_master()
        logger.info("Modification complete.")

    def set_master_envvar(self, envvar, value):
        """Add an environment variable from the mesos master context

        :param envvar: The envvar name to set
        :type envvar: str
        :param value: the value to use for envvar
        :type value: str
        """
        logger.info("Setting %s=%s", envvar, value)
        envvars = {envvar: value}
        self.set_master_envvars(envvars)

    def set_master_envvars(self, envvars):
        """Set multiple environment variables in the mesos master context

        :param envvars: A dictionary of envvar names and values
        :type envvars: dict
        """
        logger.info("Setting envvars: %s", envvars)
        def set_vars(previous_envvars, updates=envvars):
            new_envvars = previous_envvars.copy()
            new_envvars.update(updates)
            return new_envvars
        self._modify_envvars(set_vars)


    def remove_master_envvar(self, envvar):
        """Remove an an environment variable from the mesos master context

        :param envvar: The envvar name to remove
        :type envvar: str
        """
        logger.info("Unsetting %s", envvar)
        def remove_var(previous_envvars, remove_var=envvar):
            new_envvars = previous_envvars.copy()
            del new_envvars[remove_var]
            return new_envvars
        self._modify_envvars(remove_var)


    def _fetch_current_envvars(self):
        """Acquire the curent set of envvars from the mesos master and store
        them in self.master_envvars
        """
        # Get the current set of master envvars
        success, out = shakedown.run_command_on_master('cat /opt/mesosphere/etc/mesos-master')
        if success is not True:
            logger.info('Unable to get current envvars from master: {}'.format(out))
            raise RuntimeError("Unable to get current envvars")

        logger.info("Current envvars:\n{}".format(out))
        envvars, commented_lines = self._decode_master_config(out)
        self.master_envvars = envvars
        self.commented_lines = commented_lines

    def _modify_envvars(self, modifier_f):
        """Performs a transformation on the mesos master environment
        variables.  Accepts a function which accepts the mesos environment
        before the transformation, and returns the new environment.  The
        function should accept and return dicts of str:str

        :param modifier_f: The function to apply the transformation to the envvar state
        :type modifier_f: func
        """
        logger.info("Updating envvars..")
        new_envvars = modifier_f(self.master_envvars)
        self.master_envvars = new_envvars
        logger.debug("New envar set is: %s", self.master_envvars)

    def _decode_master_config(self, config_text):
        """Unpacks a mesos-master configfile to a dict of settings and a list
        of commented lines.
        """
        logger.info("Decoding mesos master configuration envvars...")
        lines = config_text.splitlines()

        envvars = {}
        commented_lines = []
        for line in lines:
            if line.lstrip().startswith('#'):
                commented_lines.append(line)
                continue
            if not line.strip():
                # not bothering with empty lines
                continue
            if '=' not in line:
                logger.warn("Unexpected config line with no '=' : %s", line)
                continue
            var, val = line.split('=', 1)
            envvars[var] = val

        return envvars, commented_lines


    def _write_envvars(self):
        logger.info("Writing envvars out to mesos master...")
        content = []
        for name, value in self.master_envvars.items():
            content.append('{}={}'.format(name, value))

        for line in self.commented_lines:
            content.append(line)

        new_file_content = '\n'.join(content)

        success, out = shakedown.run_command_on_master('sudo sh -c \'echo "{}" > /opt/mesosphere/etc/mesos-master\''.format(new_file_content))

        logger.info("Wrote new envvars:\n{}".format(out))

        if not success:
            raise RuntimeError("Failure while attempting to modify envvars on master")


    def restart_master(self):
        logger.info("Restarting master process...")

        success, out = shakedown.run_command_on_master('sudo systemctl restart dcos-mesos-master && while true; do curl leader.mesos:5050; if [ $? == 0 ]; then break; fi; done')
        if success is not True:
            raise RuntimeError("Unable to restart master: {}".format(out))

        logger.info(out)
        time.sleep(30) # XXX hack


def set_local_infinity_defaults():
    with MesosMasterModifier() as mmm:
        mmm.remove_master_envvar('MESOS_SLAVE_REMOVAL_RATE_LIMIT')
        mmm.remove_master_envvar('MESOS_MAX_SLAVE_PING_TIMEOUTS')
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
        mmm.set_master_envvars(modified_envvars)


if __name__ == "__main__":
    set_local_infinity_defaults()
