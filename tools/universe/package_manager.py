"""
A simple package manager for Universe packages.

The PackageManager class can also be used to determine the latest version of a particular package
in the Universe.
"""
import logging
import subprocess
import json
import os
import tempfile
import urllib.parse
import urllib.request

from . import package


LOGGER = logging.getLogger(__name__)


class PackageManager:
    """A simple package manager for retrieving universe packages"""
    def __init__(self,
                 universe_package_prefix="https://universe.mesosphere.com/package/",
                 dcos_version="1.11",
                 package_version="4",
                 dry_run=False):

        self._dry_run = dry_run
        self._universe_package_prefix = universe_package_prefix
        self._headers = {
            "User-Agent": "dcos/{}".format(dcos_version),
            "Accept": "application/vnd.dcos.universe.repo+json;"
                      "charset=utf-8;version=v{}".format(package_version),
        }

        self.__package_cache = {}


    def get_package_versions(self, package_name):
        """Get all versions for a specified package"""
        if self._dry_run:
            return DryRunPackages()

        if package_name not in self.__package_cache:
            LOGGER.info("Retrieving information for package: %s", package_name)
            url = urllib.parse.urljoin(self._universe_package_prefix, package_name)
            try:
                req = urllib.request.Request(url, headers=self._headers)
                with urllib.request.urlopen(req, timeout=60) as f:
                    # Returned data: {"packages": [releases for the specified package]}
                    package_releases = json.loads(f.read().decode())['packages']
                    self.__package_cache[package_name] = [package.Package.from_json(p) for p in package_releases]
            except Exception as e:
                LOGGER.error("Failed to fetch package information at %s: %s", url, e)

        return self.__package_cache.get(package_name, [])


    def get_latest(self, package_name):
        if isinstance(package_name, package.Package):
            package_name = package_name.get_name()

        all_package_versions = self.get_package_versions(package_name)
        if all_package_versions:
            # Releases should be in order of oldest to latest:
            return all_package_versions[-1]

        return None


class DryRunPackages:
    def get(self, package_name, default):
        return [package.Package(package_name,
                                package.Version(0, "DRY_RUN_VERSION")), ]
