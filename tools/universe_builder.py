#!/usr/bin/env python3

import argparse
import logging
import sys
from universe import Package
from universe import UniversePackageBuilder


logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


DESCRIPTION_STRING = """
Build a DC/OS Universe package.

For example:
$ %(prog)s kafka 1.2.3-4.5.6 \\
    /path/to/template/jsons/ \\
    https://example.com/path/to/kafka-artifacts \\
    /path/to/artifact1.zip /path/to/artifact2.zip /path/to/artifact3.zip
"""

EPILOG_STRING = "In addition, environment variables named 'TEMPLATE_SOME_PARAMETER' " \
                "will be inserted against the provided package template (with params of the " \
                " form '{{some-parameter}}')"


def main(argv):
    parser = argparse.ArgumentParser(description=DESCRIPTION_STRING,
                                     epilog=EPILOG_STRING)
    parser.add_argument('package_name', type=str,
                        help='The package name')
    parser.add_argument('package_version', type=str,
                        help='The package version string')
    parser.add_argument('package_dir_path', type=str,
                        help='The local path where the package template is located')
    parser.add_argument('upload_dir_url', type=str,
                        help='The URL of the directory where artifacts are located (S3, etc)')
    parser.add_argument('artifact_paths', type=str, nargs='+',
                        help='The artifact paths (for sha256 as needed)')

    args = parser.parse_args(argv)

    logger.info('''###
Package:         {} (version {})
Template path:   {}
Upload base dir: {}
Artifacts:       {}
###'''.format(args.package_name, args.package_version, args.package_dir_path,
              args.upload_dir_url, ','.join(args.artifact_paths)))

    package_info = Package(args.package_name, args.package_version)
    builder = UniversePackageBuilder(package_info,
                                     args.package_dir_path,
                                     args.upload_dir_url,
                                     args.artifact_paths)
    package_path = builder.build_package()
    if not package_path:
        logger.error("Error building stub universe")
        return -1
    logger.info('---')
    logger.info('Built stub universe package:')
    # print the package location as stdout (the rest of the file is stderr):
    print(package_path)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
