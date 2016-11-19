import os
import os.path
import subprocess
import sys

# platform-specific executables are expected to end with one of these suffixes:
EXE_SUFFIX_DARWIN = '-darwin'
EXE_SUFFIX_LINUX = '-linux'
EXE_SUFFIX_WINDOWS = '.exe'

# all executables are expected to live under this relative path (relative to this file):
EXE_DIRECTORY = 'binaries'

def main():
    # determine suffix based on runtime platform:
    if sys.platform.startswith('darwin'):
        find_suffix = EXE_SUFFIX_DARWIN
    elif sys.platform.startswith('linux'):
        find_suffix = EXE_SUFFIX_LINUX
    elif sys.platform.startswith('win32'):
        find_suffix = EXE_SUFFIX_WINDOWS
    else:
        print('Unsupported system platform (expected darwin/linux/win32): {}'.format(sys.platform))
        return -1

    here = os.path.abspath(os.path.dirname(__file__))

    # get full path to directory and validate presence:
    binpath = os.path.join(here, EXE_DIRECTORY)
    if not os.path.exists(binpath):
        print('Path {} not found.'.format(binpath))
        return -1
    if not os.path.isdir(binpath):
        print('Path {} is not a directory.'.format(binpath))
        return -1

    # find file with matching suffix in directory:
    for filename in os.listdir(binpath):
        if not filename.endswith(find_suffix):
            continue
        filepath = os.path.join(binpath, filename)
        args = [filepath] + sys.argv[1:]
        return subprocess.call(args)

    print('No executable in {} ending with "{}" was found.'.format(binpath, find_suffix))
    return -1


if __name__ == '__main__':
    sys.exit(main())
