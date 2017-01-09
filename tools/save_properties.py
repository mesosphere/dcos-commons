#!/usr/bin/python
#
# Saves the stub-universe.properties file by uploading it to S3.
#
# Assumption: this script is called from Jenkins where $WORKSPACE is defined.

import logging
import os
import sys

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

PROPERTIES_FILE_NAME='stub-universe.properties'


def upload_to_s3(s3_dir_uri):
    jenkins_workspace_path = os.environ.get('WORKSPACE', '')
    properties_file_path = "{}/{}".format(jenkins_workspace_path, PROPERTIES_FILE_NAME)
    if not os.path.isfile(properties_file_path):
        err = 'Could not find properties file: {}'.format(properties_file_path)
        raise Exception(err)

    # check if aws cli tools are installed
    cmd = "aws --version"
    ret = os.system(cmd)
    if not ret == 0:
        err = 'Required AWS cli tools not installed.'
        raise Exception(err)

    filename = os.path.basename(properties_file_path)
    cmd = 'aws s3 cp --acl public-read {} {}/{} 1>&2'.format(
        properties_file_path, s3_dir_uri, filename)
    logger.info(cmd)
    ret = os.system(cmd)
    if not ret == 0:
        err = 'Failed to upload {} to S3'.format(filename)
        raise Exception(err)


def main(argv):
    if len(argv) != 2:
        logger.error('Syntax: {} <S3 directory URI>'.format(argv[0]))
        logger.error('Received arguments {}'.format(str(argv)))
        return 1
    upload_to_s3(argv[1])

    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
