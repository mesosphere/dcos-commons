'''Utilities relating to reclaiming private agent disk space consumed by Mesos but not yet garbage collected'''

import shakedown


def gc_frameworks():
    for host in shakedown.get_private_agents():
        shakedown.run_command(host, "sudo rm -rf /var/lib/mesos/slave/slaves/*/frameworks/*")
