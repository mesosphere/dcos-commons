import json
import functools
from typing import Any, Dict, Union


@functools.total_ordering
class Package:
    @staticmethod
    def from_json(json: Dict[str, Any]) -> Package:
        """Construct a Package object from a json definition"""
        return Package(json["name"], Version(json["releaseVersion"], json["version"]))

    def __init__(self, name: str, version: Version, raw_data: Dict = {}) -> None:
        self._name = name
        self._version = version
        self._raw_data = raw_data

    def __eq__(self, other: Any) -> bool:
        if self.get_name() != other.get_name():
            return False

        return bool(self.get_version() == other.get_version())

    def __lt__(self, other: Any) -> bool:
        if self.get_name() < other.get_name():
            return True

        return bool(self.get_version() < other.get_version())

    def __str__(self) -> str:
        return json.dumps(
            {
                "name": self.get_name(),
                "version": self._version.package_version,
                "releaseVersion": self._version.release_version,
            }
        )

    def is_beta(self) -> bool:
        return self._name.startswith("beta-")

    def get_name(self) -> str:
        return self._name

    def get_non_beta_name(self) -> str:
        if self.is_beta():
            return self._name[5:]

        return self._name

    def get_version(self) -> Version:
        return self._version


@functools.total_ordering
class Version:
    """Encapsulates the releases-package version pair."""

    def __init__(self, release_version: Union[int, str], package_version: str) -> None:
        self.release_version = int(release_version)
        self.package_version = package_version

    def __eq__(self, other: Any) -> bool:
        return bool(self.release_version == other.release_version)

    def __lt__(self, other: Any) -> bool:
        return bool(self.release_version < other.release_version)

    def __str__(self) -> str:
        return str(self.package_version)

    def to_json(self) -> Dict[str, Any]:
        return {"release_version": self.release_version, "package_version": self.package_version}
