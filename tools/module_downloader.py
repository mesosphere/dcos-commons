import importlib
import subprocess
import sys

import venvutil

def _ensure_dir_on_syspath(path):
    if path in sys.path:
        return
    sys.path.append(path)


def install_in_dir(module, install_dir):
    pip_cmd = ['pip', 'install', module, '-t', install_dir]
    subprocess.check_call(pip_cmd)  # creates install_dir if needed
    _ensure_dir_on_syspath(install_dir)
    loadable_name = module.replace("-", "_")
    importlib.import_module(loadable_name) # fail fast


def install_in_venv(module, install_path):
    venvutil.pip_install_module(install_path, module)
