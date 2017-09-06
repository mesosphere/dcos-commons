from .version_resolver import VersionResolver
from .version_resolver import Version


def test_version_comparison():
    assert Version(0, "1.2.4") < Version(10, "1.2.4")
    assert Version(0, "1.2.5") < Version(10, "1.2.4")
    assert Version(0, "1.2.3") < Version(0, "1.2.4")

    assert Version(0, "1.2.3") == Version(0, "1.2.3")

    assert Version(10, "1.2.4") > Version(0, "1.2.4")
    assert Version(10, "1.2.4") > Version(0, "1.2.5")
    assert Version(0, "1.2.4") > Version(0, "1.2.3")


def test_no_packages(mocker):
    dummy_packages = []

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    assert version_resolver.get_package_versions('package') == []


def test_single_package_single_version(mocker):

    dummy_packages = [
        {
            'name': 'package',
            'version': '1.2.3',
            'releaseVersion': 0,
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    assert version_resolver.get_package_versions('package') == [Version(0, '1.2.3'), ]


def test_single_package_multiple_versions(mocker):

    dummy_packages = [
        {
            'name': 'package',
            'version': '1.2.3',
            'releaseVersion': 0,
        },
        {
            'name': 'package',
            'version': '1.2.4',
            'releaseVersion': 0,
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions('package')
    assert versions == [Version(0, '1.2.3'), Version(0, '1.2.4'), ]


def test_multiple_packages_single_versions(mocker):

    dummy_packages = [
        {
            'name': 'package1',
            'version': '1.2.3',
            'releaseVersion': 0,
        },
        {
            'name': 'package2',
            'version': '1.2.4',
            'releaseVersion': 0,
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions('package1')
    assert versions == [Version(0, '1.2.3'), ]
    versions = version_resolver.get_package_versions('package2')
    assert versions == [Version(0, '1.2.4'), ]


def test_multiple_packages_multiple_versions(mocker):

    dummy_packages = [
        {
            'name': 'package1',
            'version': '1.2.3',
            'releaseVersion': 0,
        },
        {
            'name': 'package2',
            'version': '1.2.4',
            'releaseVersion': 0,
        },
        {
            'name': 'package1',
            'version': '1.2.5',
            'releaseVersion': 0,
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions('package1')
    assert versions == [Version(0, '1.2.3'), Version(0, '1.2.5'), ]
    versions = version_resolver.get_package_versions('package2')
    assert versions == [Version(0, '1.2.4'), ]


def test_version_for_specified_package_not_found(mocker):
    dummy_packages = [
        {
            'name': 'package1',
            'version': '1.2.3',
            'releaseVersion': 0,
        },
        {
            'name': 'package2',
            'version': '1.2.4',
            'releaseVersion': 0,
        },
        {
            'name': 'package1',
            'version': '1.2.5',
            'releaseVersion': 0,
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions(package_name='package_not_found')
    assert versions == []


def test_latest_version(mocker):

    dummy_packages = [
        {
            'name': 'package',
            'version': '1.2.3',
            'releaseVersion': 0,
        },
        {
            'name': 'package',
            'version': '1.2.4',
            'releaseVersion': 10,
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    assert version_resolver.get_latest_version(package_name='package') == Version(10, '1.2.4')


def package_not_present_has_no_latest_version(mocker):
    dummy_packages = [
        {
            'name': 'package',
            'version': '1.2.3',
            'releaseVersion': 0,
        },
        {
            'name': 'package',
            'version': '1.2.4',
            'releaseVersion': 0,
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    assert version_resolver.get_latest_version(package_name='packge_not_present') is None
