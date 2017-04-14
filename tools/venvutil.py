#!/usr/bin/env python3

import logging
import os
import os.path
import subprocess
import sys
import tempfile
import venv

logger = logging.getLogger(__name__)
if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(message)s")


def shared_tools_venv():
    tools_dir = os.path.abspath(os.path.dirname(__file__))
    return os.path.join(tools_dir, "tools_venv")

def venv_exists(path):
    return os.path.isfile(path, 'bin', 'python')


def create_venv(path, with_pip=True, symlinks=True, py3=False):
    "Create, but do not activate, a virtual env"
    # ignoring py3; if we're already running py3, always py3
    path = os.path.abspath(path)
    builder = venv.EnvBuilder(with_pip=with_pip, symlinks=symlinks)
    builder.create(path)

def activate_venv(path):
    "Activate a given venv for the current python process."
    # "modified from virtualenv's activate_this.py", incorporated here because
    # venv does not provide.

    path = os.path.abspath(path)
    venv_bin = os.path.join(path, 'bin')

    base = path
    if sys.platform == 'win32':
        site_packages = os.path.join(base, 'Lib', 'site-packages')
    else:
        site_packages = os.path.join(base, 'lib', 'python%s' % sys.version[:3], 'site-packages')

    # if already activated, do nothing
    if site_packages == sys.path[0]:
        return

    old_os_path = os.environ.get('PATH', '')
    os.environ['PATH'] = venv_bin + os.pathsep + old_os_path

    prev_sys_path = list(sys.path)
    import site
    site.addsitedir(site_packages)
    sys.real_prefix = sys.prefix
    sys.prefix = base
    # Move the added items to the front of the path:
    new_sys_path = []
    for item in list(sys.path):
        if item not in prev_sys_path:
            new_sys_path.append(item)
            sys.path.remove(item)
    sys.path[:0] = new_sys_path

def pip_install(path, requirements_filepath):
    "Populate a venv with given requirements"
    pip_bin = os.path.join(path, 'bin', 'pip')
    run_cmd(path, [pip_bin, 'install', '-r', requirements_filepath])

def run_cmd(path, cmd, *args, **kwargs):
    "Run an external command with a particular venv"
    venv_bin = os.path.join(path, 'bin')
    if 'env' in kwargs:
        custom_env = kwargs['env']
    else:
        custom_env = os.environ.copy()
    custom_env['PATH'] = venv_bin + os.pathsep + os.environ.get('PATH', '')
    if 'PYTHONHOME' in custom_env:
        del custom_env['PYTHONHOME']
    # TODO specially handle #!/usr/bin/python commands
    logger.info("Running command %s with PATH set to %s", cmd,
                 custom_env['PATH'])
    kwargs['env'] = custom_env
    # We don't need a PYTHONPATH here, the venv python should handle this
    subprocess.check_call(cmd, *args, **kwargs)

def run_py(path, func, *args, **kwargs):
    # theoretically save and restore is possible, but probably not a good idea
    raise NotImplementedError

def create_default_requirementsfile(filename):
    with open(filename, 'w') as reqfile:
        reqfile.write('''
requests==2.10.0

-e git+https://github.com/dcos/shakedown.git@master#egg=shakedown
''')

def create_dcoscommons_venv(path):
    create_venv(path)
    req_filename = os.path.join(path, 'requirements.txt')
    create_default_requirementsfile(req_filename)
    pip_install(path, req_filename)



if __name__ == "__main__":
    # only creating default venv so far
    if len(sys.argv) < 3:
        sys.exit("Too few arguments\nusage: venvutil.py create <dir>")
    if sys.argv[1] != "create":
        sys.exit("Unknown command {}\nusage: venvutil.py create <dir>".format(sys.argv))
    venv_tgt_dir = sys.argv[2]
    os.makedirs(venv_tgt_dir)
    create_dcoscommons_venv(venv_tgt_dir)
