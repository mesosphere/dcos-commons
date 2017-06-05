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
    version_string = subprocess.check_output([pip_binary, '--version'])
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
    return os.path.join(tools_dir, "tools_py")

def venv_exists(path):
    return os.path.isfile(path, 'bin', 'python')


def create_venv(path, with_pip=True, symlinks=True):
    "Create, but do not activate, a virtual env"
    path = os.path.abspath(path)
    logger.info("using venv module at path %s", venv.__file__)
    builder = venv.EnvBuilder(with_pip=with_pip, symlinks=symlinks)


    logger.info("Creating venv at %s", path)
    logger.info("current environment is: %s", os.environ)
    #builder.create(path)
    context = builder.ensure_directories(path)
    logger.info("venv context: %s", context)
    logger.info("venv listing: %s", ", ".join(os.listdir(path)))
    builder.create_configuration(context)
    cfg_file = os.path.join(path, 'pyvenv.cfg')
    with open(cfg_file) as f:
        logger.info("venv pyvenv.cfg: %s", f.read())

    builder.setup_python(context)
    bin_dir = os.path.join(path, 'bin')
    dirents1 =  os.listdir(bin_dir)
    logger.info("After setup_python, files in %s: %s", bin_dir, ", ".join(dirents1))

    builder._setup_pip(context)
    dirents2 =  os.listdir(bin_dir)
    logger.info("After _setup_pip, files in %s: %s", bin_dir, ", ".join(dirents2))



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

def pip_install_dir(path, requirements_filepath):
    "Install python packages to a dir from a requirements file"
    pip_cmd = [get_pip3_binary(), 'install', '--target', path, '-r', requirements_filepath]
    subprocess.check_call(pip_cmd)

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
dcos-cli==0.4.16
dcos==0.4.16
dcos-shakedown
''')

def populate_dcoscommons_packagedir(path):
    req_filename = os.path.join(path, 'requirements.txt')
    create_default_requirementsfile(req_filename)
    pip_install_dir(path, req_filename)



if __name__ == "__main__":
    # only creating default venv so far
    if len(sys.argv) < 3:
        sys.exit("Too few arguments\nusage: venvutil.py create <dir>")
    if sys.argv[1] != "create":
        sys.exit("Unknown command {}\nusage: venvutil.py create <dir>".format(sys.argv))
    venv_tgt_dir = sys.argv[2]
    os.makedirs(venv_tgt_dir)
    create_dcoscommons_venv(venv_tgt_dir)
