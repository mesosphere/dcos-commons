#!/usr/bin/env python3

import logging
import os
import os.path
import shutil
import subprocess
import sys
import tempfile
import venv

logger = logging.getLogger(__name__)
if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(message)s")


pip3_binary = None
def determine_pip3_binary():
    global pip3_binary
    pip3_binary = shutil.which('pip3')
    if pip3_binary:
        return
    pip_binary = shutil.which('pip')
    version_string = str(subprocess.check_output([pip_binary, '--version']))
    if 'python 3' in version_string:
        pip3_binary = pip_binary
        return
    raise Exception("could not find pip for python3")


def get_pip3_binary():
    if not pip3_binary:
        determine_pip3_binary()
    return pip3_binary


def shared_tools_packagedir():
    tools_dir = os.path.abspath(os.path.dirname(__file__))
    package_dir = os.path.join(tools_dir, "tools_py")
    if not os.path.isdir(package_dir):
        os.mkdir(package_dir)
    return package_dir


def activate_libdir(path):
    "Activate a given dir of packages for the current python process."
    sys.path[:0] = [path]


def pip_install_dir(path, requirements_filepath):
    "Install python packages to a dir from a requirements file"
    pip_cmd = [get_pip3_binary(), 'install',
               '--target', path,
               '-r', requirements_filepath,
               ]
    # --system is a workaround for debian brain damage
    if os.path.exists("/etc/debian_version"):
        pip_cmd.append('--system')

    subprocess.check_call(pip_cmd)

def create_requirementsfile(filename, req_text=None):
    if not req_text:
        req_text = ('''
requests==2.10.0
dcoscli==0.4.16
dcos==0.4.16
dcos-shakedown
''')
    with open(filename, 'w') as reqfile:
        reqfile.write(req_text)

def populate_dcoscommons_packagedir(path, req_txt=None):
    logger.error("populating %s", path)
    req_filename = os.path.join(path, 'requirements.txt')
    logger.error("creating %s", req_filename)
    create_requirementsfile(req_filename, req_txt)
    logger.error("installing to %s using %s", path, req_filename)
    pip_install_dir(path, req_filename)


if __name__ == "__main__":
    # only creating default venv so far
    if len(sys.argv) < 3:
        sys.exit("Too few arguments\nusage: piputil.py create <dir>")
    if sys.argv[1] != "create":
        sys.exit("Unknown command {}\nusage: piputil.py create <dir>".format(sys.argv))
    venv_tgt_dir = sys.argv[2]
    os.makedirs(venv_tgt_dir)
    populate_dcoscommons_packagedir(venv_tgt_dir)
