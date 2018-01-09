#!/usr/bin/env python3

import os
import os.path
import subprocess
import sys

__PARENT_DIR_PATH = os.path.abspath(os.path.dirname(os.path.dirname(__file__)))
__PARENT_DIR_NAME = os.path.basename(__PARENT_DIR_PATH)

def __log(msg):
    sys.stderr.write(msg + '\n')


def __get_file_error(file_path):
    if not os.path.exists(file_path):
        return 'Path does not exist: {}'.format(file_path)
    if not os.path.isfile(file_path):
        return 'Path is not a regular file: {}'.format(file_path)
    if not os.access(file_path, os.X_OK):
        return 'Path is not executable: {}'.format(file_path)
    return None


def __syntax():
    available_files = []
    for root, dirs, files in os.walk(__PARENT_DIR_PATH):
        for f in files:
            file_path = os.path.join(root, f)
            # ignore self:
            if file_path == os.path.join(__PARENT_DIR_PATH, '__init__.py'):
                continue
            # ignore files that aren't regular+executable:
            file_error = __get_file_error(file_path)
            if file_error:
                continue
            # get path relative to this dir:
            available_files.append(file_path[len(__PARENT_DIR_PATH) + 1:])
    available_files.sort()

    __log('''Syntax: {} <file> [args]
{} executable files in {}:
  {}'''.format(__PARENT_DIR_NAME, len(available_files), __PARENT_DIR_PATH, '\n  '.join(available_files)))


def main():
    if len(sys.argv) < 2:
        __syntax()
        return 1

    file_to_run = sys.argv[1]
    path_to_run = os.path.join(__PARENT_DIR_PATH, file_to_run)
    file_error = __get_file_error(path_to_run)
    if file_error:
        __log(file_error)
        __syntax()
        return 1

    return subprocess.call([path_to_run] + sys.argv[2:])


if __name__ == '__main__':
    sys.exit(main())
