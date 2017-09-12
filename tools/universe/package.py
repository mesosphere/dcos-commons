import json


class Package:

    @staticmethod
    def from_json(json):
        """Construct a Package object from a json definition"""
        return Package(json['name'], Version(json['releaseVersion'], json['version']))

    def __init__(self, name, version):
        self._name = name
        self._version = version

    def __eq__(self, other):
        if self.get_name() != other.get_name():
            return False

        return self.get_version() == other.get_version()

    def __lt__(self, other):
        if self.get_name() < other.get_name():
            return True

        return self.get_version() < other.get_version()

    def __gt__(self, other):
        if self.get_name() > other.get_name():
            return True

        return self.get_version() > other.get_version()

    def __str__(self):
        return json.dumps({
            'name': self.get_name(),
            'version': str(self.get_version())
         })

    # def get_upgrades_from(self):
    #     if self._version_resolver is None:
    #         return "*"

    #     latest_version = self._version_resolver.get_latest_version(self._name)

    #     return str(latest_version) if latest_version else "*"

    # def get_downgrades_to(self):
    #     return self.get_upgrades_from()

    def get_name(self):
        return self._name

    def get_version(self):
        return self._version


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
