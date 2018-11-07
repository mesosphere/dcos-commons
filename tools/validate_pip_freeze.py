#!/usr/bin/env python3
# Verify that the installed Python modules match the requirements file provided
# as the argument.
import logging
import os.path
import pkg_resources
import re
import subprocess
import sys
import urllib.parse


_IGNORED_WHITELIST = [
    'python-apt',  # installed by apt from a deb
]
_HINT_RE = re.compile(r'.*validator-hint:( +name=(?P<name>[\w.-]+))?( +version=(?P<version>[\w.+-]+))? *$')


def _duplicates(strings: [str]) -> [str]:
    counts = {}
    for s in strings:
        if s not in counts:
            counts[s] = 0
        counts[s] += 1
    return [s for s, v in counts.items() if v > 1]


def _process_line(line: str) -> str:
    if not line.startswith('git+http'):
        return line

    parsed = urllib.parse.urlparse(line.strip())
    repo_and_sha = os.path.basename(parsed.path)
    name = os.path.splitext(repo_and_sha)[0]

    m = _HINT_RE.match(parsed.fragment)
    if m:
        if m.group('name'):
            name = m.group('name')
        version = m.group('version')
        if version:
            if version == 'SNAPSHOT':
                return '%s===%s' % (name, version)
            else:
                return '%s==%s' % (name, version)
    return name


def main(requirements_filename: str) -> int:
    with open(requirements_filename, 'r') as requirements_file:
        requirement_strings = [_process_line(line) for line in requirements_file]
        requirements = list(pkg_resources.parse_requirements(requirement_strings))
    logging.info('Loaded %d requirements from %s.', len(requirements), requirements_filename)

    duplicates = _duplicates([r.key for r in requirements])
    if duplicates:
        logging.critical('Duplicate requirements: %s', duplicates)
        return 1

    frozen_lines = [l.decode() for l in subprocess.check_output([sys.executable, '-m', 'pip', 'freeze']).split()]
    frozen_requirements = list(pkg_resources.parse_requirements(frozen_lines))
    logging.info('Loaded %d requirements from `pip freeze`', len(frozen_requirements))

    required_but_not_present = set(requirements).difference(set(frozen_requirements))
    present_but_not_required = [r for r in set(frozen_requirements).difference(set(requirements))
                                if r.key not in _IGNORED_WHITELIST]
    rc = 0
    if required_but_not_present:
        logging.error('Required but not currently present: %s', required_but_not_present)
        logging.error('Please investigate why a required module was not installed.')
        rc += 2
    if present_but_not_required:
        logging.error('Present but not required: %s', present_but_not_required)
        logging.error('Add these to requirements_frozen.txt or ignored whitelist.')
        rc += 4
    return rc


if __name__ == '__main__':
    logging.basicConfig(format="[%(asctime)s|%(name)s|%(levelname)s]: %(message)s", level=logging.INFO)
    sys.exit(main(sys.argv[1]))
