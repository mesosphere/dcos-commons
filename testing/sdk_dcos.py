'''Utilities relating to getting information about DC/OS itself

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_dcos IN ANY OTHER PARTNER REPOS
************************************************************************
'''
from enum import Enum

import sdk_cmd


class DCOS_SECURITY(Enum):
    disabled = 1
    permissive = 2
    strict = 3


def get_metadata():
    return sdk_cmd.cluster_request('GET',
                                   'dcos-metadata/bootstrap-config.json',
                                   retry=False)


def get_security_mode() -> DCOS_SECURITY:
    r = get_metadata().json()
    mode = r['security']
    return DCOS_SECURITY[mode]
