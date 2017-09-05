#!/usr/bin/env python3
"""
This module can be used to determine the latest version of a particular package
in the Universe.
"""
import requests


class VersionResolver:
    def __init__(self, pm):
        self._package_manger = pm

    def get_package_versions(self, package_name=None):
        """Get all versions for a specified package"""
        packages = self._package_manger.get_packages()

        return self._process_packages(packages, package_name)

    @staticmethod
    def _process_packages(packages, package_name):
        package_versions = {}

        for package in packages:
            name = package['name']

            if package_name is not None and package_name != name:
                continue

            version = package['version']

            for k in package.keys():
                print(k)

            if name in package_versions:
                package_versions[name].append(version)
            else:
                package_versions[name] = [version, ]

        return package_versions


if __name__ == "__main__":

    pm = package_manager.PackageManager()
    vr = VersionResolver(pm)
    package_name = 'beta-confluent-kafka'
    versions = vr.get_package_versions(package_name=package_name)

    print(versions)
