#!/usr/bin/env python3

import argparse
import itertools
import os.path
import subprocess

from typing import List


BUILD_FOLDERS = ["cli/", "clivendor/", "govendor/", "sdk/", "testing/", "tools/"]
BUILD_FILES = ["conftest.py", "test_requirements.txt", "Dockerfile"]


def get_changed_files(git_reference: str) -> List[str]:
    """
    Get the list of files changed relative to the specified git reference.
    """
    cmd = ["git", "diff", git_reference, "--name-only"]

    file_list = subprocess.check_output(cmd).decode("utf-8").split("\n")

    return file_list


def ignore_extensions(input: List[str], extensions: str) -> List[str]:
    """
    Remove files ending with any of the provided extensions, provided as a comma-separated string.

    For example: ".py,.txt,.sh"
    """
    extension_filter = tuple(f.strip() for f in extensions.split(","))
    return list(filter(lambda f: not f.lower().endswith(extension_filter), input))


def filter_extensions(input: List[str], extensions: str) -> List[str]:
    """
    Include only files ending with any of the provided extensions, provided as a comma-separated string.

    For example: ".py,.txt,.sh"
    """
    extension_filter = tuple(f.strip() for f in extensions.split(","))
    return list(filter(lambda f: f.lower().endswith(extension_filter), input))


def filter_build_files_and_folders(input: List[str]) -> List[str]:
    """
    Filter the list of files to those that would affect the build in some way.
    """
    return list(filter(lambda f: f.startswith(BUILD_FILES + BUILD_FOLDERS), input))


def filter_deleted_files(input: List[str]) -> List[str]:
    """
    Filter the list of files which no longer exist.
    """
    return list(filter(lambda f: os.path.exists(f), input))


def flatten_file_list(file_args: List[str]) -> List[str]:
    return list(itertools.chain.from_iterable([f.split() for f in file_args]))


def parse_args():
    parser = argparse.ArgumentParser(description="Filter out applicable files")

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--files", type=str, nargs="+", help="The file list to process")
    group.add_argument(
        "--from-git",
        type=str,
        nargs="?",
        const="HEAD",
        help="A git reference to use as a base for determining the list of changed files to process",
    )

    parser.add_argument("--extensions", type=str, help="A comma-separated list of extensions")
    parser.add_argument(
        "--ignore-extensions", type=str, help="A comma-separated list of extensions to ignore"
    )
    parser.add_argument("--only-build-files", action="store_true")
    parser.add_argument("--include-deleted-files", action="store_true")

    return parser.parse_args()


def main():
    args = parse_args()

    if args.from_git is not None:
        files = get_changed_files(args.from_git)
    else:
        files = flatten_file_list(args.files)

    if args.extensions:
        files = filter_extensions(files, args.extensions)
    if args.ignore_extensions:
        files = ignore_extensions(files, args.ignore_extensions)
    if args.only_build_files:
        files = filter_build_files_and_folders(files)
    if not args.include_deleted_files:
        files = filter_deleted_files(files)

    for f in files:
        print(f)


if __name__ == "__main__":
    main()
