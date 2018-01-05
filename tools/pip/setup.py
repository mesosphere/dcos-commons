#!/usr/bin/env python3

import os
import os.path
import setuptools
import shutil
import subprocess
import sys

def syntax():
    print('Syntax: OUTPUT_NAME=foo INPUT_DIR=path/to/foo VERSION=0.123.0 {} -q bdist_wheel'.format(sys.argv[0]))

def main():
    output_name = os.getenv('OUTPUT_NAME')
    input_dir_path = os.getenv('INPUT_DIR')
    version = os.getenv('VERSION')
    if not output_name or not input_dir_path or not version:
        print('Missing OUTPUT_NAME, INPUT_DIR, or VERSION envvars.')
        syntax()
        return 1

    if not os.path.isdir(input_dir_path):
        print('Provided input path is not a directory: {}'.format(input_dir_path))
        syntax()
        return 1

    # only include files that are tracked by git
    input_relative_file_paths = subprocess.check_output(['git', 'ls-files'], cwd=input_dir_path).decode('utf-8').split()
    print('Packing {} files from {} into {}-{}:\n  {}'.format(
        len(input_relative_file_paths), input_dir_path, output_name, version, '\n  '.join(input_relative_file_paths)))

    # wipe/recreate output directory
    script_dir = os.path.abspath(os.path.dirname(__file__))
    output_dir_path = os.path.join(script_dir, output_name)
    if os.path.exists(output_dir_path):
        shutil.rmtree(output_dir_path)
    os.makedirs(output_dir_path)
    build_dir_path = os.path.join(script_dir, 'build')
    if os.path.exists(build_dir_path):
        shutil.rmtree(build_dir_path)
    # copy all input files to ./<outname>/...
    for input_relative_file_path in input_relative_file_paths:
        src_path = os.path.join(input_dir_path, input_relative_file_path)
        dest_path = os.path.join(output_dir_path, input_relative_file_path)
        dest_dir = os.path.dirname(dest_path)
        if not os.path.isdir(dest_dir):
            os.makedirs(dest_dir)
        shutil.copy(src_path, dest_path)

    init_filename = '__init__.py'

    # ensure that a root-level ./<outname>/__init__.py exists, so that the python module has a __file__ attribute
    open(os.path.join(output_dir_path, init_filename), 'a').close()

    # copy cmd_wrapper entrypoint into ./<outname>/cmd_wrapper/ as well
    entrypoint_package = 'cmd_wrapper'
    endpoint_output_dir = os.path.join(output_dir_path, entrypoint_package)
    os.makedirs(endpoint_output_dir)
    shutil.copy(
        os.path.join(script_dir, entrypoint_package, init_filename),
        os.path.join(endpoint_output_dir, init_filename))
    input_relative_file_paths.append(os.path.join(entrypoint_package, init_filename))

    # run setup with list of files to include
    setuptools.setup(
        name=output_name,
        version=version,
        url='http://github.com/mesosphere/dcos-commons',
        packages=[output_name],
        entry_points={ 'console_scripts': [
            '{} = {}.{}:main'.format(output_name, output_name, entrypoint_package) ] },
        package_data={ output_name: input_relative_file_paths })

    # clean up build detritus:
    shutil.rmtree(build_dir_path)
    shutil.rmtree(os.path.join(script_dir, '{}.egg-info'.format(output_name)))
    shutil.rmtree(output_dir_path)
    # move whl file into script dir:
    output_file = '{}-{}-py3-none-any.whl'.format(output_name, version)
    output_path = os.path.join(script_dir, output_file)
    os.rename(os.path.join(script_dir, 'dist', output_file), output_path)
    shutil.rmtree(os.path.join(script_dir, 'dist'))

    print('''Built {}-{}: {}'''.format(output_name, version, output_path))
    return 0

if __name__ == '__main__':
    sys.exit(main())
