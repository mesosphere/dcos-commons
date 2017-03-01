import os
import os.path
import setuptools
import shutil
import sys

import bin_wrapper

_suffixes = [
    bin_wrapper.EXE_SUFFIX_DARWIN,
    bin_wrapper.EXE_SUFFIX_LINUX,
    bin_wrapper.EXE_SUFFIX_WINDOWS
]

_output_file = 'bin_wrapper-0.0.1-py2.py3-none-any.whl'

def syntax():
    print('Syntax: EXE_BUILD_DIR=/path/to/cli/exes {} bdist_wheel'.format(sys.argv[0]))

def main():
    script_dir = os.path.abspath(os.path.dirname(__file__))
    package_dir = 'bin_wrapper'

    exe_build_dir = os.environ.get('EXE_BUILD_DIR', '')
    if not exe_build_dir:
        print('Missing EXE_BUILD_DIR envvar: Path of directory containing CLI binaries')
        syntax()
        return 1
    # exe names should match the directory (go convention):
    exe_base_name = os.path.basename(exe_build_dir.rstrip('/'))

    if not os.path.isdir(exe_build_dir):
        print('Provided path to executables is not a directory: {}'.format(exe_build_dir))
        syntax()
        return 1
    bin_paths = [
        os.path.join(exe_build_dir, '{}{}'.format(exe_base_name, suffix)) for suffix in _suffixes
    ]

    for path in bin_paths:
        if not os.path.isfile(path):
            print('Executable path does not exist: {}'.format(path))
            syntax()
            return 1

    print('Packing {} executables in python wrapper:\n- {}'.format(
        len(bin_paths), '\n- '.join(bin_paths)))

    # wipe/recreate 'binaries' directory
    binaries_dir = 'binaries'
    binaries_dir_path = os.path.join(script_dir, package_dir, binaries_dir)
    if os.path.exists(binaries_dir_path):
        shutil.rmtree(binaries_dir_path)
    os.makedirs(binaries_dir_path)
    build_dir_path = os.path.join(script_dir, 'build')
    if os.path.exists(build_dir_path):
        shutil.rmtree(build_dir_path)
    # copy everything from ../BINNAME to ./bin_wrapper/binaries/BINNAME
    for bin_path in bin_paths:
        shutil.copy(
            os.path.join(script_dir, bin_path),
            os.path.join(binaries_dir_path, os.path.basename(bin_path)))

    # run setup with generated MANIFEST.in
    packages = setuptools.find_packages(exclude=['contrib', 'docs', 'tests'])
    entry_points = { 'console_scripts': [ '{}={}:main'.format(exe_base_name, package_dir) ] }
    package_data = { package_dir: [ os.path.join(binaries_dir, os.path.basename(f)) for f in bin_paths ] }

    setuptools.setup(
        name=package_dir,
        version='0.0.1',
        url='http://mesosphere.com',
        packages=packages,
        entry_points=entry_points,
        package_data=package_data)

    # clean up copied binaries:
    shutil.rmtree(build_dir_path)
    shutil.rmtree(os.path.join(script_dir, 'bin_wrapper.egg-info'))
    shutil.rmtree(binaries_dir_path)
    # move whl file into binary dir:
    os.rename(
        os.path.join(script_dir, 'dist', _output_file),
        os.path.join(exe_build_dir, _output_file))
    shutil.rmtree(os.path.join(script_dir, 'dist'))
    return 0

if __name__ == '__main__':
    sys.exit(main())
