#!/usr/bin/env python3

import logging
import os
import os.path
import time
import random
import string

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

class S3Uploader(object):

    def __init__(self, package_name, dry_run=False):
        # check if aws cli tools are installed
        cmd = "aws --version"
        ret = os.system(cmd)
        if not ret == 0:
            raise Exception('Required AWS cli tools not installed.')

        s3_bucket = os.environ.get('S3_BUCKET')
        if not s3_bucket:
            s3_bucket = 'infinity-artifacts'
        log.info('Using artifact bucket: {}'.format(s3_bucket))

        s3_dir_path = os.environ.get('S3_DIR_PATH', 'autodelete7d')
        dir_name = '{}-{}'.format(
            time.strftime("%Y%m%d-%H%M%S"),
            ''.join([random.SystemRandom().choice(string.ascii_letters + string.digits) for i in range(16)]))

        # sample s3_directory: 'infinity-artifacts/autodelete7d/kafka/20160815-134747-S6vxd0gRQBw43NNy'
        self._s3_directory = os.environ.get(
            'S3_URL',
            's3://{}/{}/{}/{}'.format(
                s3_bucket,
                s3_dir_path,
                package_name,
                dir_name))

        self._http_directory = os.environ.get(
            'ARTIFACT_DIR',
            'https://{}.s3.amazonaws.com/{}/{}/{}'.format(
                s3_bucket,
                s3_dir_path,
                package_name,
                dir_name))

        self._aws_region = os.environ.get('AWS_UPLOAD_REGION', '')
        self._dry_run = dry_run


    def get_http_directory(self):
        return self._http_directory


    def get_s3_directory(self):
        return self._s3_directory


    def upload(self, filepath, content_type=None):
        filename = os.path.basename(filepath)
        cmdlist = ['aws s3']
        if self._aws_region:
            cmdlist.append('--region={}'.format(self._aws_region))
        cmdlist.append('cp --acl public-read')
        if content_type:
            cmdlist.append('--content-type "{}"'.format(content_type))
        cmdlist.append('{} {}/{} 1>&2'.format(filepath, self._s3_directory, filename))
        cmd = ' '.join(cmdlist)
        if self._dry_run:
            log.info('[DRY RUN] {}'.format(cmd))
            ret = 0
        else:
            log.info(cmd)
            ret = os.system(cmd)
        if not ret == 0:
            raise Exception('Failed to upload {} to S3'.format(filename))
        return '{}/{}'.format(self._http_directory, filename)
