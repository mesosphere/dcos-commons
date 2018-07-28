'''Utilities relating to fetching agent information and manipulating agent host systems

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_agents IN ANY OTHER PARTNER REPOS
************************************************************************
'''

import logging
import retrying
import traceback

import sdk_cmd

log = logging.getLogger(__name__)


def _is_public_agent(agent):
    # Note: We could also check "'public_ip' in agent['attributes']", but it's unclear how many
    # DC/OS versions that would work with. For now, let's just go with the known-good method of
    # checking for pre-reserved resources under a 'slave_public' role.
    for reservation in agent['reserved_resources']:
        if 'slave_public' in reservation:
            return True
    return False


def get_public_agents():
    return [a for a in get_agents() if _is_public_agent(a)]


def get_private_agents():
    return [a for a in get_agents() if not _is_public_agent(a)]


def get_agents():
    return sdk_cmd.cluster_request('GET', '/mesos/slaves').json()['slaves']


def shutdown_agent(agent_ip):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=30 * 60 * 1000,
        retry_on_result=lambda res: not res)
    def fn():
        ok, stdout = sdk_cmd.agent_ssh(agent_ip, 'sudo shutdown -h +1')
        log.info('Shutdown agent {}: ok={}, stdout="{}"'.format(agent_ip, ok, stdout))
        return ok
    # Might not be able to connect to the agent on first try so we repeat until we can
    fn()

    # We use a manual check to detect that the host is down. Mesos takes ~5-20 minutes to detect a
    # dead agent, so relying on Mesos to tell us this isn't really feasible for a test.

    log.info('Waiting for agent {} to appear inactive in /mesos/slaves'.format(agent_ip))

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=5 * 60 * 1000,
        retry_on_result=lambda res: res)
    def wait_for_unresponsive_agent():
        try:
            response = sdk_cmd.cluster_request('GET', '/mesos/slaves', retry=False).json()
            agent_statuses = {}
            for agent in response['slaves']:
                agent_statuses[agent['hostname']] = agent['active']
            log.info('Wait for {}=False: {}'.format(agent_ip, agent_statuses))
            # If no agents were found, try again
            if len(agent_statuses) == 0:
                return True
            # If other agents are listed, but not OUR agent, assume that OUR agent is now inactive.
            # (Shouldn't happen, but just in case...)
            return agent_statuses.get(agent_ip, False)
        except Exception as e:
            log.info(e)
            log.info(traceback.format_exc())
            # Try again. Wait for the ip to be definitively inactive.
            return True

    wait_for_unresponsive_agent()

    log.info('Agent {} appears inactive in /mesos/slaves, proceeding.'.format(agent_ip))


def partition_agent(agent_host: str):
    # copy current rules
    sdk_cmd.agent_ssh(agent_host, 'if [ ! -e iptables.rules ] ; then sudo iptables -L > /dev/null && sudo iptables-save > iptables.rules ; fi')
    # flush all rules
    sdk_cmd.agent_ssh(agent_host, 'sudo iptables -F INPUT')
    # allow all traffic
    sdk_cmd.agent_ssh(agent_host, ' && '.join(
        'sudo iptables --policy INPUT ACCEPT',
        'sudo iptables --policy OUTPUT ACCEPT',
        'sudo iptables --policy FORWARD ACCEPT'))
    # configure rules to cut off mesos but allow reconnect later:
    sdk_cmd.agent_ssh(agent_host, ' && '.join(
        'sudo iptables -I INPUT -p tcp --dport 22 -j ACCEPT',  # allow SSH
        'sudo iptables -I INPUT -p icmp -j ACCEPT',  # allow ping
        'sudo iptables -I OUTPUT -p tcp --sport 5051 -j REJECT',  # disallow mesos
        'sudo iptables -A INPUT -j REJECT'))  # disallow all other input


def reconnect_agent(agent_host: str):
    # restore prior rules:
    sdk_cmd.agent_ssh(agent_host, 'if [ -e iptables.rules ]; then sudo iptables-restore < iptables.rules && rm iptables.rules ; fi')
