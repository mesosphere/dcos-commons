#!/usr/bin/env python3

import logging
import os
import os.path
import subprocess

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class S3Uploader(object):
    def __init__(self, s3_directory, dry_run=False):
        # check if aws cli tools are installed
        if not subprocess.run("aws --version".split()).returncode == 0:
            raise Exception('Required "aws" command is not installed.')

        self._s3_directory = s3_directory
        self._aws_region = os.environ.get('AWS_UPLOAD_REGION', '')
        self._dry_run = dry_run

    def get_s3_directory(self):
        return self._s3_directory

    def upload(self, filepath, content_type=None):
        filename = os.path.basename(filepath)
        cmdlist = ['aws s3']
        if self._aws_region:
            cmdlist.append('--region={}'.format(self._aws_region))
        cmdlist.append('cp --acl public-read')
        if self._dry_run:
            cmdlist.append('--dryrun')
        if content_type is not None:
            cmdlist.append('--content-type "{}"'.format(content_type))
        dest_url = '{}/{}'.format(self._s3_directory, filename)
        cmdlist.append('{} {} 1>&2'.format(filepath, dest_url))
        cmd = ' '.join(cmdlist)
        log.info(cmd)
        ret = os.system(cmd)
        if not ret == 0:
            raise Exception('Failed to upload {} to {}'.format(filepath, dest_url))
