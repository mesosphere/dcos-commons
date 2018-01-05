import pytest
import tempfile

import sdk_jobs


@pytest.mark.smoke
@pytest.mark.broken
def test_metronome():
    """
    This test is marked as "broken" and will remain so until this JIRA is resolved:
    https://jira.mesosphere.com/browse/DCOS_OSS-2036
    """
    job = {
        'description': 'Test Metronome API regressions',
        'id': 'test.metronome',
        'run': {
            'cmd': 'ls',
            'docker': {'image': 'busybox:latest'},
            'cpus': 1,
            'mem': 512,
            'user': 'nobody',
            'restart': {'policy': 'ON_FAILURE'}
        }
    }

    tmp_dir = tempfile.mkdtemp(prefix='metronome-test')
    sdk_jobs.install_job(job, tmp_dir=tmp_dir)
    sdk_jobs.run_job(job)
    sdk_jobs.remove_job(job)
