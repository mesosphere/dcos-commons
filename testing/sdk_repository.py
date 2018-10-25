"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_repository IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import itertools
import json
import logging
import os

import sdk_cmd
import sdk_utils

log = logging.getLogger(__name__)

_STUB_UNIVERSE_URL_ENVVAR = "STUB_UNIVERSE_URL"


def parse_stub_universe_url_string(stub_universe_url_string):
    """Handles newline-, space-, and comma-separated strings."""

    if stub_universe_url_string.strip().lower() == "none":
        # User is explicitly requesting to test released versions of the package.
        # "none" must be explicitly specified to avoid accidentally testing the wrong version.
        return []

    def flatmap(f, items):
        """
        lines = ["one,two", "three", "four,five"]
        f     = lambda s: s.split(",")

        >>> map(f, lines)
        [['one', 'two'], ['three'], ['four', 'five']]

        >>> flatmap(f, lines)
        ['one', 'two', 'three', 'four', 'five']
        """
        return itertools.chain.from_iterable(map(f, items))
    lines = stub_universe_url_string.split()
    urls = list(filter(None, flatmap(lambda s: s.split(","), lines)))

    if not urls:
        # User didn't specify "none", yet we still didn't get any urls.
        # However, this check isn't perfect. Maybe the user gave us a valid but unrelated URL? Or gave us one URL correctly but should have given multiple?
        raise Exception("Invalid {env}. Provide comma and/or newline-separated URL(s), or specify '{env}=none' to test release versions.".format(env=_STUB_UNIVERSE_URL_ENVVAR))
    return urls


def get_repos() -> list:
    # prepare needed universe repositories
    stub_universe_url_string = os.environ.get(_STUB_UNIVERSE_URL_ENVVAR, "")
    return parse_stub_universe_url_string(stub_universe_url_string)


def remove_repo(repo_name) -> bool:
    rc, stdout, stderr = sdk_cmd.run_cli("package repo remove {}".format(repo_name))
    if stderr.endswith("is not present in the list"):
        # tried to remove something that wasn't there, move on.
        return True
    return rc == 0


def add_repo(repo_name, repo_url, index=None) -> bool:
    index_arg = "" if index is None else " --index={}".format(index)
    rc, _, _ = sdk_cmd.run_cli(
        "package repo add{} {} {}".format(index_arg, repo_name, repo_url)
    )
    return rc == 0


def add_stub_universe_urls(stub_universe_urls: list) -> dict:
    stub_urls = {}

    if not stub_universe_urls:
        return stub_urls

    # clean up any duplicate repositories
    _, current_universes, _ = sdk_cmd.run_cli("package repo list --json")
    for repo in json.loads(current_universes)["repositories"]:
        if repo["uri"] in stub_universe_urls:
            log.info("Removing duplicate stub URL: {}".format(repo["uri"]))
            assert remove_repo(repo["name"])

    # add the needed universe repositories
    log.info("Adding stub URLs: {}".format(stub_universe_urls))
    for url in stub_universe_urls:
        assert add_repo("testpkg-{}".format(sdk_utils.random_string()), url, 0)

    return stub_urls


def remove_universe_repos(stub_urls):
    # clear out the added universe repositories at testing end
    for name, url in stub_urls.items():
        log.info("Removing stub URL: {}".format(url))
        assert remove_repo(name)


def universe_session():
    """Add the universe package repositories defined in $STUB_UNIVERSE_URL.

    This should generally be used as a fixture in a framework's conftest.py:

    @pytest.fixture(scope='session')
    def configure_universe():
        yield from sdk_repository.universe_session()
    """
    stub_urls = {}
    try:
        stub_urls = add_stub_universe_urls(get_repos())
        yield
    finally:
        remove_universe_repos(stub_urls)
