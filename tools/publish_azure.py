#!/usr/bin/env python3
#
# Uploads artifacts to Blob Storage.
# Produces a universe, and uploads it to Blob Storage.
#
# Env:
#   AZURE_STORAGE_URL (Azure storage account blob service access)
#   AZURE_STORAGE_CONNECTION_STRING (Azure storage account access key)

import logging
import os
import os.path
import random
import string
import sys
import time
import subprocess

import universe
from universe.package import Version

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class AzurePublisher(object):
    def __init__(self, package_name, package_version, input_dir_path, artifact_paths):
        self._dry_run = os.environ.get("DRY_RUN", "")
        self._pkg_name = package_name
        self._pkg_version = package_version
        self._input_dir_path = input_dir_path
        self._az_storage_account = os.environ.get("AZURE_STORAGE_ACCOUNT", "")
        self._az_container_name = os.environ.get("AZURE_CONTAINER_NAME", "")

        if self._az_storage_account == "" or self._az_container_name == "":
            raise Exception("It's mandatory to define the environment variables: 'AZURE_STORAGE_ACCOUNT' and 'AZURE_CONTAINER_NAME'")

        if not os.path.isdir(input_dir_path):
            raise Exception("Provided package path is not a directory: {}".format(input_dir_path))

        self._artifact_paths = []
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                err = "Provided package path is not a file: {} (full list: {})".format(
                    artifact_path, artifact_paths
                )
                raise Exception(err)
            self._artifact_paths.append(artifact_path)

        self._uploader = universe.AzureUploader(self._az_storage_account, self._az_container_name, self._dry_run)


    def _spam_universe_url(self, universe_url):
        """Write jenkins properties file to $WORKSPACE/<pkg_version>.properties:"""
        jenkins_workspace_path = os.environ.get("WORKSPACE", "")
        if jenkins_workspace_path:
            properties_file = open(
                os.path.join(jenkins_workspace_path, "{}.properties".format(self._pkg_version)), "w"
            )
            properties_file.write("STUB_UNIVERSE_URL={}\n".format(universe_url))
            properties_file.write(
                "STUB_UNIVERSE_AZURE_CONTAINER={}\n".format(self._az_container_name)
            )
            properties_file.flush()
            properties_file.close()

        # write URL to provided text file path:
        universe_url_path = os.environ.get("UNIVERSE_URL_PATH", "")
        if universe_url_path:
            universe_url_file = open(universe_url_path, "w")
            universe_url_file.write("{}\n".format(universe_url))
            universe_url_file.flush()
            universe_url_file.close()


    def upload(self):
        """Generates a container if not exists, then uploads artifacts and a new stub universe to that container"""
        version = Version(release_version=0, package_version=self._pkg_version)
        package_info = universe.Package(name=self._pkg_name, version=version)
        package_manager = universe.PackageManager(dry_run=self._dry_run)
        builder = universe.UniversePackageBuilder(
            package_info,
            package_manager,
            self._input_dir_path,
            "https://{}.blob.core.windows.net/{}".format(self._az_storage_account, self._az_container_name),
            self._artifact_paths,
            self._dry_run,
        )
        universe_path = builder.build_package()

        # upload universe package definition first and get its URL
        self._uploader.upload(
            universe_path, content_type="application/vnd.dcos.universe.repo+json;charset=utf-8"
        )

        # Get the stub-universe.json file URL from Azure CLI
        universe_url = subprocess.check_output(
            "az storage blob url -o tsv --account-name {} --container-name {} --name {}"\
                .format(self._az_storage_account, 
                    self._az_container_name,
                    os.path.basename(universe_path))\
                .split()
        ).decode('ascii').rstrip()

        logger.info("Uploading {} artifacts:".format(len(self._artifact_paths)))

        logger.info("---")
        logger.info("STUB UNIVERSE: {}".format(universe_url))
        logger.info("---")

        for path in self._artifact_paths:
            self._uploader.upload(path)

        self._spam_universe_url(universe_url)

        logger.info("---")
        logger.info("(Re)install your package using the following commands:")
        logger.info("dcos package uninstall {}".format(self._pkg_name))
        logger.info("\n- - - -\nFor 1.9 or older clusters only")
        logger.info(
            "dcos node ssh --master-proxy --leader "
            + '"docker run mesosphere/janitor /janitor.py -r {0}-role -p {0}-principal -z dcos-service-{0}"'.format(
                self._pkg_name
            )
        )
        logger.info("- - - -\n")
        logger.info("dcos package repo remove {}-azure".format(self._pkg_name))
        logger.info(
            "dcos package repo add --index=0 {}-azure '{}'".format(self._pkg_name, universe_url)
        )
        logger.info("dcos package install --yes {}".format(self._pkg_name))

        return universe_url



def print_help(argv):
    logger.info(
        "Syntax: {} <package-name> <template-package-dir> [artifact files ...]".format(argv[0])
    )
    logger.info(
        "  Example: $ {} hello-world /path/to/universe/jsons/ /path/to/artifact1.zip /path/to/artifact2.zip /path/to/artifact3.zip".format(
            argv[0]
        )
    )
    logger.info(
        "In addition, environment variables named 'TEMPLATE_SOME_PARAMETER' will be inserted against the provided package template (with params of the form '{{some-parameter}}')"
    )


def main(argv):
    if len(argv) < 3:
        print_help(argv)
        return 1
    # the package name:
    package_name = argv[1]
    # the package version:
    package_version = argv[2]
    # local path where the package template is located:
    package_dir_path = argv[3].rstrip("/")
    # artifact paths (to upload along with stub universe)
    artifact_paths = argv[4:]
    logger.info(
        """###
Package:         {}
Version:         {}
Template path:   {}
Artifacts:
{}
###""".format(
            package_name,
            package_version,
            package_dir_path,
            "\n".join(["- {}".format(path) for path in artifact_paths]),
        )
    )

    AzurePublisher(package_name, package_version, package_dir_path, artifact_paths).upload()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
