#!/usr/bin/env python3

import configparser
import logging
import os
import os.path
import subprocess

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class S3Uploader(object):
    def __init__(self, s3_directory, dry_run=False):
        # check if aws cli tools are installed
        if subprocess.run('aws --version'.split()).returncode != 0:
            raise Exception('Required "aws" command is not installed.')

        self._s3_directory = s3_directory
        self._aws_region = os.environ.get('AWS_UPLOAD_REGION', '')
        self._reauth_attempted = False
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

        # try once:
        ret = os.system(cmd)
        if ret != 0:
            if self._renew_credentials():
                # renew succeeded, try again:
                ret = os.system(cmd)
        if ret != 0:
            # failed once, then renewal failed or retry failed
            raise Exception('Failed to upload {} to {}'.format(filepath, dest_url))

    def _renew_credentials(self):
        if self._reauth_attempted:
            # reauth was already tried once during this session. any failure must be from something else.
            return False
        self._reauth_attempted = True

        # check that maws is installed before trying anything else
        if subprocess.run('which maws'.split(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0:
            log.error('Unable to renew credentials: Missing "maws" command in PATH')
            return False

        # renew creds using discovered aws profile
        aws_profile = self._get_aws_profile()
        log.info('Upload attempt failed, renewing credentials for AWS profile {}'.format(aws_profile))
        ret = os.system('maws login {}'.format(aws_profile))
        if ret != 0:
            raise Exception('Failed to renew credentials for AWS profile {}'.format(aws_profile))
        return True

    def _get_aws_profile(self):
        aws_profile = os.getenv('AWS_PROFILE')
        if aws_profile:
            return aws_profile

        creds_path = os.path.expanduser('~/.aws/credentials')
        if not os.path.isfile(creds_path):
            raise Exception('Unable to renew credentials: No AWS_PROFILE and no ~/.aws/credentials')

        config = configparser.ConfigParser()
        try:
            config.read(creds_path)
        except Exception:
            raise Exception('Unable to renew credentials: No AWS_PROFILE and credentials file is unparseable: {}'.format(creds_path))

        profile_names = config.sections()
        if len(profile_names) == 0:
            raise Exception('Unable to renew credentials: No AWS_PROFILE and no profiles found in credentials file: {}'.format(creds_path))
        elif len(profile_names) == 1:
            return profile_names[0]
        else:
            raise Exception('Unable to renew credentials: No AWS_PROFILE and multiple profiles found in credentials file: {}'.format(creds_path))
