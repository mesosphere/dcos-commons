#!/usr/bin/env python3

import logging
import subprocess
import os

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class AzureUploader(object):
    def __init__(self, storage_account, container_name, dry_run=False):
        # check if az cli tools are installed
        if subprocess.run("az --version".split()).returncode != 0:
            raise Exception('Required "az" command is not installed.')

        self._container_name = container_name
        self._storage_account = storage_account
        self._dry_run = dry_run

    def upload(self, filepath, content_type=None):
        filename = os.path.basename(filepath)
        log.info("Uploading {}...".format(filename))
        cmdlist = ["az", "storage", "blob", "upload", "--validate-content", "-o", "none"]
        cmdlist += "--account-name {} --container-name {}".format(
            self._storage_account, self._container_name
        ).split(" ")
        if content_type is not None:
            cmdlist += "--content-type {}".format(content_type).split(" ")
        cmdlist += "--file {} --name {}".format(filepath, filename).split(" ")

        # Runs Azure CLI command and try to capture possible exceptions
        output = ""
        if self._dry_run != "True":
            try:
                output = subprocess.call(cmdlist)
            except Exception:
                # Azure CLI doesn't stores any session details. Only a token which expires after 90 days of inactivity.
                log.error(
                    "Common Error: Check if token has expired. Try to relogin with 'az login' and repeat the building"
                )
                log.error(output)
                raise

        else:
            log.info("Uploading '{}' file ({})".format(filename, " ".join(cmdlist)))
