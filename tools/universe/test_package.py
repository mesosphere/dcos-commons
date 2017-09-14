from .package import Package
from .package import Version


def test_version_comparison():
    assert Version(0, "1.2.4") < Version(10, "1.2.4")
    assert Version(0, "1.2.5") < Version(10, "1.2.4")
    assert Version(0, "1.2.3") < Version(0, "1.2.4")

    assert Version(0, "1.2.3") == Version(0, "1.2.3")

    assert Version(10, "1.2.4") > Version(0, "1.2.4")
    assert Version(10, "1.2.4") > Version(0, "1.2.5")
    assert Version(0, "1.2.4") > Version(0, "1.2.3")


def test_package_from_json():
    package_json = {
        'name': 'package',
        'version': '1.2.3',
        'releaseVersion': 10
    }
    p = Package.from_json(package_json)

    assert p.get_name() == package_json['name']
    assert p.get_version() == Version(10, '1.2.3')


def test_package_starts_with_beta_is_beta():
    p = Package('beta-package', None)

    assert p.is_beta()


def test_normal_package_is_not_beta():

    p = Package('package', None)

    assert not p.is_beta()


def test_non_beta_backage_beta_name_is_name():

    p = Package('package', None)

    assert p.get_name() == p.get_non_beta_name()

def test_beta_package_beta_name():
    p = Package('beta-package', None)

    assert p.get_non_beta_name() == 'package'

