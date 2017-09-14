import json
import functools

@functools.total_ordering
class Package:
    @staticmethod
    def from_json(json):
        """Construct a Package object from a json definition"""
        return Package(json['name'],
                       Version(json['releaseVersion'], json['version']))

    def __init__(self, name, version, raw_data={}):
        self._name = name
        self._version = version
        self._raw_data = raw_data

    def __eq__(self, other):
        if self.get_name() != other.get_name():
            return False

        return self.get_version() == other.get_version()

    def __lt__(self, other):
        if self.get_name() < other.get_name():
            return True

        return self.get_version() < other.get_version()

    def __str__(self):
        return json.dumps({
            'name': self.get_name(),
            'version': self._version.package_version,
            'releaseVersion': self._version.release_version,
        })

    def is_beta(self):
        return self._name.startswith('beta-')

    def get_name(self):
        return self._name

    def get_non_beta_name(self):
        if self.is_beta():
            return self._name[5:]

        return self._name

    def get_version(self):
        return self._version


@functools.total_ordering
class Version:
    """Encapsulates the releases-package version pair."""

    def __init__(self, release_version, package_version):
        self.release_version = int(release_version)
        self.package_version = package_version

    def __eq__(self, other):
        return self.release_version == other.release_version

    def __lt__(self, other):
        return self.release_version < other.release_version

    def __str__(self):
        return str(self.package_version)

    def to_json(self):
        return {
            'release_version': self.release_version,
            'package_version': self.package_version,
        }
