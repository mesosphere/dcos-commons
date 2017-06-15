import json
import os

import shakedown


PACKAGE_NAME = 'cassandra'
DEFAULT_TASK_COUNT = 3


WRITE_DATA_JOB_FILENAME = 'write-data.json.template'
VERIFY_DATA_JOB_FILENAME = 'verify-data.json.template'
DELETE_DATA_JOB_FILENAME = 'delete-data.json.template'
VERIFY_DELETION_JOB_FILENAME = 'verify-deletion.json.template'


def get_job_dict(
        job_filename,
        node_address = os.getenv('CASSANDRA_NODE_ADDRESS', 'node-0-server.cassandra.autoip.dcos.thisdcos.directory'),
        node_port = os.getenv('CASSANDRA_NODE_PORT', '9042')):
    jobs_dir = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'jobs')
    template_filename = os.path.join(jobs_dir, job_filename)
    with open(template_filename, 'r') as f:
        job_contents = f.read()
    job_contents = job_contents.replace('{{NODE_ADDRESS}}', node_address)
    job_contents = job_contents.replace('{{NODE_PORT}}', node_port)
    return json.loads(job_contents)


WRITE_DATA_JOB = get_job_dict(WRITE_DATA_JOB_FILENAME)
VERIFY_DATA_JOB = get_job_dict(VERIFY_DATA_JOB_FILENAME)
DELETE_DATA_JOB = get_job_dict(DELETE_DATA_JOB_FILENAME)
VERIFY_DELETION_JOB = get_job_dict(VERIFY_DELETION_JOB_FILENAME)


TEST_JOBS = (
    WRITE_DATA_JOB, VERIFY_DATA_JOB, DELETE_DATA_JOB, VERIFY_DELETION_JOB
)
