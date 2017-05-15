import logging
import os
import subprocess

import module_downloader

logger = logging.getLogger(__name__)


working=False


def setup(install_dir):
    """Try to download and import junit_xml"""
    try:
        global working
        module_downloader.install_in_dir('junit-xml', install_dir)
        working=True
    except Exception as e:
        logger.exception("Failed to load junit-xml")
    return ok()


def ok():
    return working

def emit_junit_xml(cluster_launch_attempts, frameworks):
    """Write out all the test actions failures to a junit file for jenkins or
    similar"""
    if not ok():
        return
    import junit_xml
    launch_fake_testcases = []
    for launch_attempt in cluster_launch_attempts:
        attempt_duration = launch_attempt.end_time - launch_attempt.start_time
        fake_test = junit_xml.TestCase(launch_attempt.name,
                                       elapsed_sec=attempt_duration)
        if launch_attempt.launch_succeeded:
            fake_test.stdout = "Launch worked"
        else:
            fake_test.add_failure_info("Launch failed")
        launch_fake_testcases.append(fake_test)

    launch_suite = junit_xml.TestSuite("Cluster launches",
            launch_fake_testcases)

    fake_suites = []
    fake_suites.append(launch_suite)
    for framework in frameworks:
        framework_testcases = []
        for action_name, action in framework.actions.items():
            action_duration = action['finish'] - action['start']
            fake_test = junit_xml.TestCase(action_name,
                                           elapsed_sec=action_duration,
                                           stdout = action['stdout'],
                                           stderr = action['stderr'])
            if not action['ok']:
                message = action['error_message']
                if not message:
                    message = "%s failed" % action_name
                fake_test.add_failure_info(message, action['error_output'])
            framework_testcases.append(fake_test)
        framework_suite = junit_xml.TestSuite("%s actions" % framework.name,
                framework_testcases)
        fake_suites.append(framework_suite)

    with open("junit_testpy.xml", "w") as f:
        junit_xml.TestSuite.to_file(f, fake_suites)
