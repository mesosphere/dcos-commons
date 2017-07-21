import json
import os
import random
import string

import shakedown


def add_universe_repos():
    """Add the universe package repositories defined in $STUB_UNIVERSE_URL.

    This should generally be used in conjunction with remove_universe_repos()
    as a fixture in a framework's conftest.py:

    @pytest.fixture(scope='session')
    def configure_universe():
        stub_urls = {}
        try:
            stub_urls = repository.add_universe_repos()
            yield # let the test session execute
        finally:
            repository.remove_universe_repos(stub_urls)
    """
    stub_urls = {}

    # prepare needed universe repositories
    stub_universe_urls = os.environ.get('STUB_UNIVERSE_URL')
    if not stub_universe_urls:
        return stub_urls
    for url in stub_universe_urls.split():
        print('url: {}'.format(url))
        package_name = 'testpkg-'
        package_name += ''.join(random.choice(string.ascii_lowercase + string.digits) for _ in range(8))
        stub_urls[package_name] = url

    # clean up any duplicate repositories
    current_universes, _, _ = shakedown.run_dcos_command('package repo list --json')
    for repo in json.loads(current_universes)['repositories']:
        if repo['uri'] in stub_urls.values():
            remove_package_cmd = 'package repo remove {}'.format(repo['name'])
            shakedown.run_dcos_command(remove_package_cmd)

    # add the needed universe repositories
    for name, url in stub_urls.items():
        add_package_cmd = 'package repo add --index=0 {} {}'.format(name, url)
        shakedown.run_dcos_command(add_package_cmd)

    return stub_urls


def remove_universe_repos(stub_urls):
    """Remove universe package repositories see add_universe_repos() for more info."""
    # clear out the added universe repositores at testing end
    for name, url in stub_urls.items():
        remove_package_cmd = 'package repo remove {}'.format(name)
        out, err, rc = shakedown.run_dcos_command(remove_package_cmd)
        if err and err.endswith('is not present in the list'):
            # tried to remove something that wasn't there, move on.
            pass
