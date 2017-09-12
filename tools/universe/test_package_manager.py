from .package_manager import PackageManager
from .package import Package
from .package import Version


def create_package_manager(mocker, packages):
    """A utility function to create a package manager that returns the specified list
    of packages"""
    pm = PackageManager()
    pm._get_packages = mocker.MagicMock(return_value=packages)

    return pm


def test_no_packages(mocker):
    dummy_packages = []
    pm = create_package_manager(mocker, dummy_packages)
    assert pm.get_package_versions("package") == []


def test_single_package_single_version(mocker):

    dummy_packages = [
        {
            "name": "package",
            "version": "1.2.3",
            "releaseVersion": 0,
        },
    ]

    pm = create_package_manager(mocker, dummy_packages)

    print(Package.from_json(dummy_packages[0]))
    print(Package("package", Version(0, "1.2.3")))

    assert pm.get_package_versions("package") == [Package("package", Version(0, "1.2.3")), ]


def test_single_package_multiple_versions(mocker):

    dummy_packages = [
        {
            "name": "package",
            "version": "1.2.3",
            "releaseVersion": 0,
        },
        {
            "name": "package",
            "version": "1.2.4",
            "releaseVersion": 0,
        },
    ]

    pm = create_package_manager(mocker, dummy_packages)
    versions = pm.get_package_versions("package")
    assert [p.get_version() for p in versions] == [Version(0, "1.2.3"), Version(0, "1.2.4"), ]


def test_multiple_packages_single_versions(mocker):

    dummy_packages = [
        {
            "name": "package1",
            "version": "1.2.3",
            "releaseVersion": 0,
        },
        {
            "name": "package2",
            "version": "1.2.4",
            "releaseVersion": 0,
        },
    ]

    pm = create_package_manager(mocker, dummy_packages)

    versions = pm.get_package_versions("package1")
    assert versions == [Package("package1", Version(0, "1.2.3")), ]
    versions = pm.get_package_versions("package2")
    assert versions == [Package("package2", Version(0, "1.2.4")), ]


def test_multiple_packages_multiple_versions(mocker):

    dummy_packages = [
        {
            "name": "package1",
            "version": "1.2.3",
            "releaseVersion": 0,
        },
        {
            "name": "package2",
            "version": "1.2.4",
            "releaseVersion": 0,
        },
        {
            "name": "package1",
            "version": "1.2.5",
            "releaseVersion": 0,
        },
    ]

    pm = create_package_manager(mocker, dummy_packages)

    versions = pm.get_package_versions("package1")
    assert [p.get_version() for p in versions] == [Version(0, "1.2.3"), Version(0, "1.2.5"), ]
    versions = pm.get_package_versions("package2")
    assert [p.get_version() for p in versions] == [Version(0, "1.2.4"), ]


def test_version_for_specified_package_not_found(mocker):
    dummy_packages = [
        {
            "name": "package1",
            "version": "1.2.3",
            "releaseVersion": 0,
        },
        {
            "name": "package2",
            "version": "1.2.4",
            "releaseVersion": 0,
        },
        {
            "name": "package1",
            "version": "1.2.5",
            "releaseVersion": 0,
        },
    ]

    pm = create_package_manager(mocker, dummy_packages)

    versions = pm.get_package_versions(package_name="package_not_found")
    assert versions == []


def test_latest_version(mocker):

    dummy_packages = [
        {
            "name": "package",
            "version": "1.2.3",
            "releaseVersion": 0,
        },
        {
            "name": "package",
            "version": "1.2.4",
            "releaseVersion": 10,
        },
    ]

    pm = create_package_manager(mocker, dummy_packages)

    assert pm.get_latest(package_name="package").get_version() == Version(10, "1.2.4")


def package_not_present_has_no_latest_version(mocker):
    dummy_packages = [
        {
            "name": "package",
            "version": "1.2.3",
            "releaseVersion": 0,
        },
        {
            "name": "package",
            "version": "1.2.4",
            "releaseVersion": 0,
        },
    ]

    pm = create_package_manager(mocker, dummy_packages)

    assert pm.get_latest(package_name="packge_not_present") is None
