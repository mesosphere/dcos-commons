#!/usr/bin/env python3
"""
This module can be used to determine the latest version of a particular package
in the Universe.
"""


class Version:
    """Encapsulates the releases-package version pair."""
    def __init__(self, release_version, package_version):
        self.release_version = release_version
        self.package_version = package_version

    def __eq__(self, other):
        if self.release_version != other.release_version:
            return False

        return self.package_version == other.package_version

    def __lt__(self, other):
        if self.release_version < other.release_version:
            return True

        return self.package_version < other.package_version

    def __gt__(self, other):
        if self.release_version > other.release_version:
            return True

        return self.package_version > other.package_version

    def __str__(self):
        return str(self.package_version)


class VersionResolver:
    def __init__(self, package_manager):
        self._package_manger = package_manager

    def get_package_versions(self, package_name):
        """Get all versions for a specified package"""

        packages = self._package_manger.get_packages()

        package_versions = []

        for package in packages:
            name = package['name']

            if package_name != name:
                continue

            package_versions.append(Version(package['releaseVersion'], package['version']))

        return package_versions

    def get_latest_version(self, package_name):
        package_versions = self.get_package_versions(package_name)

        if package_versions:
            return sorted(package_versions)[-1]

        return None
