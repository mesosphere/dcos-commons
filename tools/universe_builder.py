import logging
import sys
from universe import Package
from universe import UniversePackageBuilder


logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


def print_help(argv):
    logger.info(
        'Syntax: {} <package-name> <package-version> <template-package-dir> <artifact-base-path> [artifact files ...]'.format(argv[0]))
    logger.info(
        '  Example: $ {} kafka 1.2.3-4.5.6 /path/to/template/jsons/ https://example.com/path/to/kafka-artifacts /path/to/artifact1.zip /path/to/artifact2.zip /path/to/artifact3.zip'.format(argv[0]))
    logger.info(
        'In addition, environment variables named \'TEMPLATE_SOME_PARAMETER\' will be inserted against the provided package template (with params of the form \'{{some-parameter}}\')')


def main(argv):
    if len(argv) < 5:
        print_help(argv)
        return 1
    # the package name:
    package_name = argv[1]
    # the package version:
    package_version = argv[2]
    # local path where the package template is located:
    package_dir_path = argv[3].rstrip('/')
    # url of the directory where artifacts are located (S3, etc):
    upload_dir_url = argv[4].rstrip('/')
    # artifact paths (for sha256 as needed)
    artifact_paths = argv[5:]
    logger.info('''###
Package:         {} (version {})
Template path:   {}
Upload base dir: {}
Artifacts:       {}
###'''.format(package_name, package_version, package_dir_path, upload_dir_url, ','.join(artifact_paths)))

    package_info = Package(package_name, package_version)
    package_path = UniversePackageBuilder(package_info, package_dir_path, upload_dir_url, artifact_paths).build_package()
    if not package_path:
        return -1
    logger.info('---')
    logger.info('Built stub universe package:')
    # print the package location as stdout (the rest of the file is stderr):
    print(package_path)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
