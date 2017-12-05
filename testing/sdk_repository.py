'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_repository IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json
import logging
import os
import random
import string

import shakedown
import sdk_cmd

log = logging.getLogger(__name__)


def add_universe_repos():
    log.info('Adding universe repos')

    # prepare needed universe repositories
    stub_universe_urls = os.environ.get('STUB_UNIVERSE_URL', "")

    return add_stub_universe_urls(stub_universe_urls.split(","))


def add_stub_universe_urls(stub_universe_urls: list) -> dict:
    stub_urls = {}

    if not stub_universe_urls:
        return stub_urls

    log.info('Adding stub URLs: {}'.format(stub_universe_urls))
    for url in stub_universe_urls:
        log.info('url: {}'.format(url))
        package_name = 'testpkg-'
        package_name += ''.join(random.choice(string.ascii_lowercase +
                                              string.digits) for _ in range(8))
        stub_urls[package_name] = url

    # clean up any duplicate repositories
    current_universes = sdk_cmd.run_cli('package repo list --json')
    for repo in json.loads(current_universes)['repositories']:
        if repo['uri'] in stub_urls.values():
            log.info('Removing duplicate stub URL: {}'.format(repo['uri']))
            sdk_cmd.run_cli('package repo remove {}'.format(repo['name']))

    # add the needed universe repositories
    for name, url in stub_urls.items():
        log.info('Adding stub URL: {}'.format(url))
        sdk_cmd.run_cli('package repo add --index=0 {} {}'.format(name, url))

    log.info('Finished adding universe repos')

    return stub_urls


def remove_universe_repos(stub_urls):
    log.info('Removing universe repos')

    # clear out the added universe repositores at testing end
    for name, url in stub_urls.items():
        log.info('Removing stub URL: {}'.format(url))
        out, err, rc = shakedown.run_dcos_command('package repo remove {}'.format(name))
        if err:
            if err.endswith('is not present in the list'):
                # tried to remove something that wasn't there, move on.
                pass
            else:
                raise 'Failed to remove stub repo: stdout=[{}], stderr=[{}]'.format(out, err)

    log.info('Finished removing universe repos')


def universe_session():
    """Add the universe package repositories defined in $STUB_UNIVERSE_URL.

    This should generally be used as a fixture in a framework's conftest.py:

    @pytest.fixture(scope='session')
    def configure_universe():
        yield from sdk_repository.universe_session()
    """
    stub_urls = {}
    try:
        stub_urls = add_universe_repos()
        yield
    finally:
        remove_universe_repos(stub_urls)
