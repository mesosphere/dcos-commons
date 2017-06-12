#!/usr/bin/python3

import subprocess
import sys

def fetch_origin_master():
    subprocess.check_call([b'git', b'fetch', b'origin', b'master', b'--quiet'])

def get_changed_files():
    fetch_origin_master()

    output = subprocess.check_output([b'git', b'diff', b'origin/master',
                                      b'--name-only'])
    files = output.splitlines()
    return files

def categorize_file(filename):
    if filename.endswith(b".md"):
        return "DOC"
    if filename.startswith(b"doc/"):
        return "DOC"
    if filename.startswith(b"frameworks/"):
        literal, specific_framework, rest = filename.split(b'/', 2)
        return specific_framework.decode('utf-8')
    return "SDK"

def categorize_branch_changes():
    file_types = set()
    for filename in get_changed_files():
        file_type = categorize_file(filename)
        file_types.add(file_type)

    # any SDK changes means build/test everything
    if "SDK" in file_types:
        return {"SDK"}
    # only DOC changes means build/test nothing
    if file_types == {"DOC"}:
        return {"DOC"}
    # Otherwise return the frameworks changed
    file_types.discard("DOC")
    return file_types

saved_branch_changes = None
def memoized_branch_changes():
    global saved_branch_changes
    if not saved_branch_changes:
        saved_branch_changes = categorize_branch_changes()
    return saved_branch_changes

get_branch_changetypes=memoized_branch_changes

__all__ = ('get_branch_changetypes',)

if __name__ == "__main__":
    for item in get_branch_changetypes():
        sys.stdout.write(item + "\n")
