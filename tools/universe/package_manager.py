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

from . import package
try:
    import requests
    _HAS_REQUESTS = True
except ImportError:
    _HAS_REQUESTS = False


LOGGER = logging.getLogger(__name__)


class PackageManager:
    """A simple package manager for retrieving universe packages"""
    def __init__(self, universe_url="https://universe.mesosphere.com/repo",
                 dcos_version="1.10",
                 package_version="4"):

        self._universe_url = universe_url
        self._headers = {
            "User-Agent": "dcos/{}".format(dcos_version),
            "Accept": "application/vnd.dcos.universe.repo+json;"
                      "charset=utf-8;version=v{}".format(package_version),
        }

        self.__package_cache = None

        if _HAS_REQUESTS:
            self._get_packages = _get_packages_with_requests
        else:
            self._get_packages = _get_packages_with_curl

    def get_package_versions(self, package_name):
        """Get all versions for a specified package"""

        packages = self.get_packages()

        return packages.get(package_name, [])

    def get_latest(self, package_name):
        if isinstance(package_name, package.Package):
            package_name = package_name.get_name()

        all_package_versions = self.get_package_versions(package_name)

        if all_package_versions:
            return all_package_versions[-1]

        return None

    def get_packages(self):
        """Query the uninverse to get a list of packages"""
        if not self.__package_cache:
            LOGGER.info("Package cache is empty. Retrieving package information")
            raw_package_list = self._get_packages(self._universe_url, self._headers)

            package_dict = {}
            for p in raw_package_list:
                package_name = p['name']
                package_object = package.Package.from_json(p)

                if package_name in package_dict:
                    package_dict[package_name].append(package_object)
                else:
                    package_dict[package_name] = [package_object, ]

            self.__package_cache = {}
            for p, packages in package_dict.items():
                self.__package_cache[p] = sorted(packages)

        return self.__package_cache


def _get_packages_with_curl(universe_url, headers):
    """Use curl to download the packages from the universe"""
    with tempfile.TemporaryDirectory() as tmp_dir:
        tmp_filename = os.path.join(tmp_dir, 'packages.json')

        cmd = ["curl",
               "--write-out", "%{http_code}",
               "--silent",
               "-L",
               "--max-time", "5",
               "-X", "GET",
               "-o", tmp_filename, ]
        for k, header in headers.items():
            cmd.extend(["-H", "{}: {}".format(k, header)])

        cmd.append(universe_url)

        try:
            output = subprocess.check_output(cmd)
            status_code = int(output)

            if status_code != 200:
                raise Exception("Curl returned status code {}".format(status_code))

            with open(tmp_filename, "r") as f:
                packages = json.load(f)['packages']
        except Exception as e:
            LOGGER.error("Retrieving packages with curl failed. %s", e)
            packages = []

    return packages


def _get_packages_with_requests(universe_url, headers):
    """Use the requests module to get the packages from the universe"""
    try:
        response = requests.get(universe_url, headers=headers)
        response.raise_for_status()
        packages = response.json()['packages']
    except Exception as e:
        LOGGER.error("Retrieving packages with requests failed. %s", e)
        packages = []

    return packages
