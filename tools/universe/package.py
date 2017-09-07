from .package_manager import PackageManager
from .version_resolver import VersionResolver


class Package:

    def __init__(self, name, version, version_resolver=None):
        self._name = name
        self._version = version

        self._set_version_resolver(version_resolver)

    def _set_version_resolver(self, version_resolver):
        if version_resolver is None:
            self._version_resolver = VersionResolver(PackageManager())
        else:
            self._version_resolver = version_resolver

    def get_upgrades_from(self):
        if self._version_resolver is None:
            return "*"

        latest_version = self._version_resolver.get_latest_version(self._name)

        return str(latest_version) if latest_version else "*"

    def get_downgrades_to(self):
        return self.get_upgrades_from()

    def get_name(self):
        return self._name

    def get_version(self):
        return self._version
