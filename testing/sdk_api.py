'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_api IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging

import dcos
import shakedown

log = logging.getLogger(__name__)


def get(service_name, endpoint):
    '''
    :param endpoint: endpoint of the form /v1/...
    :type endpoint: str
    :returns: JSON response from the provided scheduler API endpoint
    :rtype: Response
    '''

    # Some of our APIs (cough looking at you plans)
    # will return a meaningful 417.
    def is_success(status_code):
        return (200 <= status_code < 300) or status_code == 417


    response = dcos.http.get("{}{}".format(
        shakedown.dcos_service_url(service_name),
        endpoint),
        is_success=is_success)

    # 417 == plan error
    if response.status_code != 417:
        response.raise_for_status()

    return response


def is_suppressed(service_name):
    response = get(service_name, "/v1/state/properties/suppressed")
    log.info("{} suppressed={}".format(service_name, response.content))
    return response.content == b"true"
