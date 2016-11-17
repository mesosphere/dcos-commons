import os.path
import setuptools
import shutil
import sys

# These are the only things you should need to edit:

# 1. Name used for the frontend executable in [pyenv]/bin. MUST be prefixed by 'dcos-':
exe_name = 'dcos-kafka'

# 2. Paths to the executables, relative to this file:
relative_bin_paths = [
    '../{0}/{0}-darwin'.format(exe_name),
    '../{0}/{0}-linux'.format(exe_name),
    '../{0}/{0}.exe'.format(exe_name)
]

def main():
    here = os.path.abspath(os.path.dirname(__file__))
    package_dir = 'bin_wrapper'

    # wipe/recreate 'binaries' directory
    binaries_dir = 'binaries'
    bindirpath = os.path.join(here, package_dir, binaries_dir)
    if os.path.exists(bindirpath):
        shutil.rmtree(bindirpath)
    os.makedirs(bindirpath)
    # copy everything from ../BINNAME to ./bin_wrapper/binaries/BINNAME
    for bin_path in relative_bin_paths:
        shutil.copy(
            os.path.join(here, bin_path),
            os.path.join(bindirpath, os.path.basename(bin_path)))

    # run setup with generated MANIFEST.in
    setuptools.setup(
        name=package_dir,
        version='0.0.1',
        url='http://mesosphere.com',
        packages=setuptools.find_packages(exclude=['contrib', 'docs', 'tests']),
        entry_points={ 'console_scripts': [ '{}={}:main'.format(exe_name, package_dir) ] },
        package_data={ package_dir: [ os.path.join(binaries_dir, os.path.basename(f)) for f in relative_bin_paths] })

    # clean up binaries afterwards:
    shutil.rmtree(bindirpath)
    return 0

if __name__ == '__main__':
    sys.exit(main())
