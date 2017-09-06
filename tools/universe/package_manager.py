"""
A simple package manager for Universe packages.
"""
import logging
import subprocess
import json
import os
import tempfile
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

        self.universe_url = "https://universe.mesosphere.com/repo"
        self._headers = {
            "User-Agent": "dcos/{}".format(dcos_version),
            "Accept": "application/vnd.dcos.universe.repo+json;"
                      "charset=utf-8;version=v{}".format(package_version),
        }

        self.__package_cache = None

    def get_packages(self):
        """Query the uninverse to get a list of packages"""
        if not self.__package_cache:
            if not _HAS_REQUESTS:
                LOGGER.info("Requests package not found. Using curl")
                self.__package_cache = self._get_packages_with_curl()
            else:
                self.__package_cache = self._get_packages_with_requests()

        return self.__package_cache

    def _get_packages_with_curl(self):
        """Use curl to download the packages from the universe"""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_filename = os.path.join(tmp_dir, 'packages.json')

            cmd = ["curl",
                   "--write-out",  "%{http_code}",
                   "--silent",
                   "-L",
                   "--max-time", "5",
                   "-X", "GET",
                   "-o", tmp_filename, ]
            for k, header in self._headers.items():
                cmd.extend(["-H", "{}: {}".format(k, header)])

            cmd.append(self.universe_url)

            try:
                output = subprocess.check_output(cmd)
                status_code = int(output)

                if status_code != 200:
                    raise Exception("Curl returned status code %s", status_code)

                with open(tmp_filename, "r") as f:
                    packages = json.load(f)['packages']
            except Exception as e:
                LOGGER.error("Retrieving packages with curl failed. %s", e)
                packages = []

        return packages

    def _get_packages_with_requests(self):
        """Use the requests module to get the packages from the universe"""
        try:
            response = requests.get(self.universe_url, headers=self._headers)
            response.raise_for_status()
            packages = response.json()['packages']
        except Exception as e:
            LOGGER.error("Retrieving packages with requests failed. %s", e)
            packages = []

        return packages
