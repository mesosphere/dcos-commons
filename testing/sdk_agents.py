"""Utilities relating to fetching agent information and manipulating agent host systems

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_agents IN ANY OTHER PARTNER REPOS
************************************************************************
"""

import logging
import retrying
import traceback
from typing import Any, Dict, List

import sdk_cmd
import sdk_utils

log = logging.getLogger(__name__)


def _is_public_agent(agent: Dict[str, Any]) -> bool:
    # Note: We could also check "'public_ip' in agent['attributes']", but it's unclear how many
    # DC/OS versions that would work with. For now, let's just go with the known-good method of
    # checking for pre-reserved resources under a 'slave_public' role.
    for reservation in agent["reserved_resources"]:
        if "slave_public" in reservation:
            return True
    return False


def get_public_agents() -> List[Dict[str, Any]]:
    return [a for a in get_agents() if _is_public_agent(a)]


def get_private_agents() -> List[Dict[str, Any]]:
    return [a for a in get_agents() if not _is_public_agent(a)]


def get_agents() -> List[Dict[str, Any]]:
    response = sdk_cmd.cluster_request("GET", "/mesos/slaves")
    response_json = response.json()
    return list(response_json["slaves"])


def shutdown_agent(agent_host: str) -> None:
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=30 * 60 * 1000, retry_on_result=lambda res: not res
    )
    def fn() -> bool:
        rc, stdout, _ = sdk_cmd.agent_ssh(agent_host, "sudo shutdown -h +1")
        log.info('Shutdown agent {}: rc={}, stdout="{}"'.format(agent_host, rc, stdout))
        return rc == 0

    # Might not be able to connect to the agent on first try so we repeat until we can
    fn()

    # We use a manual check to detect that the host is down. Mesos takes ~5-20 minutes to detect a
    # dead agent, so relying on Mesos to tell us this isn't really feasible for a test.

    log.info("Waiting for agent {} to appear inactive in /mesos/slaves".format(agent_host))

    @retrying.retry(wait_fixed=1000, stop_max_delay=5 * 60 * 1000, retry_on_result=lambda res: res)
    def wait_for_unresponsive_agent() -> bool:
        try:
            response = sdk_cmd.cluster_request("GET", "/mesos/slaves", retry=False).json()
            agent_statuses = {}
            for agent in response["slaves"]:
                agent_statuses[agent["hostname"]] = agent["active"]
            log.info("Wait for {}=False: {}".format(agent_host, agent_statuses))
            # If no agents were found, try again
            if len(agent_statuses) == 0:
                return True
            # If other agents are listed, but not OUR agent, assume that OUR agent is now inactive.
            # (Shouldn't happen, but just in case...)
            return agent_statuses.get(agent_host, False)
        except Exception as e:
            log.info(e)
            log.info(traceback.format_exc())
            # Try again. Wait for the ip to be definitively inactive.
            return True

    wait_for_unresponsive_agent()

    log.info("Agent {} appears inactive in /mesos/slaves, proceeding.".format(agent_host))


def partition_agent(agent_host: str) -> None:
    rc, _, _ = sdk_cmd.agent_ssh(
        agent_host,
        " && ".join(
            [
                # Nice to have for any debugging.
                "hostname",
                "echo Saving current rules...",
                "sudo iptables -L > /dev/null",
                "sudo iptables-save > iptables.backup",
                "echo Flushing rules...",
                "sudo iptables -F INPUT",
                "echo Allowing all traffic...",
                "sudo iptables --policy INPUT ACCEPT",
                "sudo iptables --policy OUTPUT ACCEPT",
                "sudo iptables --policy FORWARD ACCEPT",
                "echo Cutting off mesos...",
                "sudo iptables -I INPUT -p tcp --dport 22 -j ACCEPT",  # allow SSH
                "sudo iptables -I INPUT -p icmp -j ACCEPT",  # allow ping
                "sudo iptables -I OUTPUT -p tcp --sport 5051 -j REJECT",  # disallow mesos
                "sudo iptables -A INPUT -j REJECT",  # disallow all other input
            ]
        ),
    )
    assert rc == 0, "Failed to partition agent"


def reconnect_agent(agent_host: str) -> None:
    # restore prior rules:
    rc, _, _ = sdk_cmd.agent_ssh(
        agent_host,
        " && ".join(
            [
                # Nice to have for any debugging.
                "hostname",
                "echo Restoring previous rules...",
                "sudo iptables-restore < iptables.backup",
                "sudo rm -f iptables.backup",
            ]
        ),
    )
    assert rc == 0, "Failed to reconnect agent"


def decommission_agent(agent_id: str) -> None:
    assert sdk_utils.dcos_version_at_least("1.11"),\
        "node decommission is supported in DC/OS 1.11 and above only"
    rc, _, _ = sdk_cmd.run_cli(
        "node decommission {}".format(agent_id)
    )
    assert rc == 0


def drain_agent(agent_id: str, decommission: bool = False, wait: bool = False, max_grace_period: int = -1) -> None:
    assert sdk_utils.dcos_version_at_least("1.13"), "node drain is supported in DC/OS 1.13 and above only"

    # Base command.
    drain_cmd = ["node", "drain", agent_id]

    # Attach options.
    if decommission:
        drain_cmd.append("--decommission")
    if wait:
        drain_cmd.append("--wait")
    if max_grace_period != -1:
        drain_cmd.append("--max-grace-period={}".format(max_grace_period))

    rc, _, _ = sdk_cmd.run_cli(" ".join(drain_cmd) )
    assert rc == 0


def reactivate_agent(agent_id: str) -> None:
    assert sdk_utils.dcos_version_at_least("1.13"), "node reactivate is supported in DC/OS 1.13 and above only"

    # Base command.
    reactivate_cmd = ["node", "reactivate", agent_id]

    rc, _, _ = sdk_cmd.run_cli(" ".join(reactivate_cmd) )
    assert rc == 0

