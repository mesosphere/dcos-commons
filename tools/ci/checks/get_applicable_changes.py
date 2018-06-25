#!/usr/bin/env python3

import argparse
import itertools

from typing import List


def ignore_files(input: List[str]) -> List[str]:
    ignored_extensions = (".md",)
    return list(filter(lambda f: not f.lower().endswith(ignored_extensions), input))


def filter_extensions(input: List[str], extensions: str) -> List[str]:
    if not extensions:
        return input
    extension_filter = tuple(f.strip() for f in extensions.split(","))
    return list(filter(lambda f: f.lower().endswith(extension_filter), input))


def filter_files_and_folders(input: List[str]) -> List[str]:
    folders = ("cli/", "clivendor/", "govendor/", "sdk/", "testing/", "tools/")
    files = ("conftest.py", "test_requirements.txt")
    return list(filter(lambda f: f.startswith(folders + files), input))


def flatten_file_list(file_args: List[str]) -> List[str]:
    return list(itertools.chain.from_iterable([f.split() for f in file_args]))


def parse_args():
    parser = argparse.ArgumentParser(description="Filter out applicable files")
    parser.add_argument("files", type=str, nargs="+", help="The file list to process")
    parser.add_argument(
        "--extensions", type=str, help="A comma-separated list of extensions"
    )
    return parser.parse_args()


def main():
    args = parse_args()

    files = flatten_file_list(args.files)

    for f in filter_files_and_folders(
        ignore_files(filter_extensions(files, args.extensions))
    ):
        print(f)


if __name__ == "__main__":
    main()
