#!/usr/bin/python3
#
# Deprecated. Please call publish_aws.py with the same arguments.

import logging
import sys

import publish_aws


if __name__ == '__main__':
    logging.error("####")
    logging.error("## CI_UPLOAD.PY IS DEPRECATED.")
    logging.error("## You should be calling publish_aws.py (with the same args) instead.")
    logging.error("####")
    sys.exit(publish_aws.main(sys.argv))
