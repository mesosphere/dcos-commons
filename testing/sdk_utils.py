
import shakedown
import logging


def test_output(msg):
    '''Emit an informational message on test progress during test runs'''
    logger = logging.getLogger(__name__)
    logger.info(msg)

def gc_frameworks():
    '''Reeclaims private agent disk space consumed by Mesos but not yet garbage collected'''
    for host in shakedown.get_private_agents():
        shakedown.run_command(host, "sudo rm -rf /var/lib/mesos/slave/slaves/*/frameworks/*")
