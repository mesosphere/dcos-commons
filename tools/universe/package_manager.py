"""
A simple package manager for Universe packages.
"""
import requests


class PackageManager:

    def __init__(self, universe_url="https://universe.mesosphere.com/repo", dcos_version="1.10", package_version="4"):

        self.universe_url = "https://universe.mesosphere.com/repo"
        self._headers = {
            "User-Agent": "dcos/{}".format(dcos_version),
            "Accept": "application/vnd.dcos.universe.repo+json;"
                      "charset=utf-8;version=v{}".format(package_version),
        }

    def get_packages(self):
        """Query the uninverse to get a list of packages"""
        response = requests.get(self.universe_url, headers=self._headers)
        response.raise_for_status()

        return response.json()['packages']
