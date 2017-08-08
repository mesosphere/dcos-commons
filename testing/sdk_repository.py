import json
import logging
import os
import random
import string

import shakedown

log = logging.getLogger(__name__)


def add_universe_repos():
    stub_urls = {}

    log.info('Adding universe repos')

    # prepare needed universe repositories
    stub_universe_urls = os.environ.get('STUB_UNIVERSE_URL')
    if not stub_universe_urls:
        return stub_urls

    log.info('Adding stub URLs: {}'.format(stub_universe_urls))
    for url in stub_universe_urls.split():
        print('url: {}'.format(url))
        package_name = 'testpkg-'
        package_name += ''.join(random.choice(string.ascii_lowercase + string.digits) for _ in range(8))
        stub_urls[package_name] = url

    # clean up any duplicate repositories
    current_universes, _, _ = shakedown.run_dcos_command('package repo list --json')
    for repo in json.loads(current_universes)['repositories']:
        if repo['uri'] in stub_urls.values():
            log.info('Removing duplicate stub URL: {}'.format(repo['uri']))
            remove_package_cmd = 'package repo remove {}'.format(repo['name'])
            shakedown.run_dcos_command(remove_package_cmd)

    # add the needed universe repositories
    for name, url in stub_urls.items():
        log.info('Adding stub URL: {}'.format(url))
        add_package_cmd = 'package repo add --index=0 {} {}'.format(name, url)
        shakedown.run_dcos_command(add_package_cmd)

    log.info('Finished adding universe repos')

    return stub_urls


def remove_universe_repos(stub_urls):
    log.info('Removing universe repos')

    # clear out the added universe repositores at testing end
    for name, url in stub_urls.items():
        log.info('Removing stub URL: {}'.format(url))
        remove_package_cmd = 'package repo remove {}'.format(name)
        out, err, rc = shakedown.run_dcos_command(remove_package_cmd)
        if err and err.endswith('is not present in the list'):
            # tried to remove something that wasn't there, move on.
            pass

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
