from version_resolver import VersionResolver


def test_no_packages(mocker):
    dummy_packages = []

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    assert version_resolver.get_package_versions() == {}


def test_single_package_single_version(mocker):

    dummy_packages = [
        {
            'name': 'package',
            'version': '1.2.3'
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    assert version_resolver.get_package_versions() == {
        'package': [
            '1.2.3',
        ]
    }


def test_single_package_multiple_versions(mocker):

    dummy_packages = [
        {
            'name': 'package',
            'version': '1.2.3'
        },
        {
            'name': 'package',
            'version': '1.2.4'
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions()
    assert list(versions.keys()) == ['package', ]
    assert versions['package'] == ['1.2.3', '1.2.4']


def test_multiple_packages_single_versions(mocker):

    dummy_packages = [
        {
            'name': 'package1',
            'version': '1.2.3'
        },
        {
            'name': 'package2',
            'version': '1.2.4'
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions()
    assert sorted(list(versions.keys())) == [
        'package1',
        'package2',
    ]
    assert versions['package1'] == [
        '1.2.3',
    ]
    assert versions['package2'] == [
        '1.2.4',
    ]


def test_multiple_packages_multiple_versions(mocker):

    dummy_packages = [
        {
            'name': 'package1',
            'version': '1.2.3'
        },
        {
            'name': 'package2',
            'version': '1.2.4'
        },
        {
            'name': 'package1',
            'version': '1.2.5'
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions()
    assert sorted(list(versions.keys())) == [
        'package1',
        'package2',
    ]
    assert versions['package1'] == [
        '1.2.3',
        '1.2.5',
    ]
    assert versions['package2'] == [
        '1.2.4',
    ]


def test_version_for_specified_package(mocker):

    dummy_packages = [
        {
            'name': 'package1',
            'version': '1.2.3'
        },
        {
            'name': 'package2',
            'version': '1.2.4'
        },
        {
            'name': 'package1',
            'version': '1.2.5'
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions(package_name='package1')
    assert list(versions.keys()) == [
        'package1',
    ]
    assert sorted(versions['package1']) == [
        '1.2.3',
        '1.2.5',
    ]


def test_version_for_specified_package_not_found(mocker):
    dummy_packages = [
        {
            'name': 'package1',
            'version': '1.2.3'
        },
        {
            'name': 'package2',
            'version': '1.2.4'
        },
        {
            'name': 'package1',
            'version': '1.2.5'
        },
    ]

    pm = mocker.Mock()
    pm.get_packages = mocker.MagicMock(return_value=dummy_packages)

    version_resolver = VersionResolver(pm)

    versions = version_resolver.get_package_versions(package_name='package_not_found')
    assert versions == {}
