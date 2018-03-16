#!/usr/bin/env python3
#
# Validates that the supplied framework (identified by the directory passed in) will function, to the best
# of our knowledge, in an airgapped cluster.
#
# The checks are:
#   - No external URIs defined in anything but resource.json
#   - No images defined in anything but resource.json (and templated into the svc.yml)
#

import re
import sys
import os


def extract_uris(file_name):
    with open(file_name, "r") as file:
        lines = file.readlines()

    matcher = re.compile(".*https?:\/\/([^\/\?]*)", re.IGNORECASE)
    matches = []
    for line in lines:
        line = line.strip()
        # Do not grab comments
        if line.startswith("*") or line.startswith("#") or line.startswith("//"):
            continue
        # Do not grab "id" lines
        if '"id":' in line:
            continue

        match = matcher.match(line)
        if match:
            matches.append(match.group(1))

    return matches


def validate_uris_in(file_name):
    uris = extract_uris(file_name)

    bad_uri = False
    for uri in uris:
        if is_bad_uri(uri, file_name):
            bad_uri = True

    return not bad_uri


def is_bad_uri(uri, file_name):
    # A FQDN is a valid cluster internal FQDN if it contains one of the listed exceptions
    exceptions = [
        ".thisdcos",
        ".mesos:",
        "$MESOS_CONTAINER_IP",
        "${MESOS_CONTAINER_IP}",
        "{{FRAMEWORK_HOST}}",
        "$FRAMEWORK_HOST",
        "${FRAMEWORK_HOST}",
    ]

    # Are any of the exceptions present?
    for exception in exceptions:
        if exception in uri:
            return False

    print("Found a bad URI:", uri, "in:", file_name,
                "Export URIs to resource.json to allow packaging for airgapped clusters.")

    return True

def get_files_to_check_for_uris(framework_directory):
    # There's a set of files that will always be present.
    files = [os.path.join(framework_directory, "universe", "config.json"),
             os.path.join(framework_directory, "universe", "marathon.json.mustache")]

    # Always check every file in the `dist` directory of the scheduler.
    dist_dir = os.path.join(framework_directory, "src", "main", "dist")

    for dp, dn, filenames in os.walk(dist_dir):
        for file in filenames:
            files.append(os.path.join(dp, file))

    return files


def validate_all_uris(framework_directory):
    bad_file = False
    files = get_files_to_check_for_uris(framework_directory)
    for file in files:
        if not validate_uris_in(file):
            bad_file = True

    return not bad_file


def validate_images(framework_directory):
    files = get_files_to_check_for_uris(framework_directory)

    for file in files:
        with open(file, "r") as file:
            lines = file.readlines()

        bad_image = False
        for line in lines:
            line = line.strip()
            if "image:" in line:
                image_matcher = re.compile("image:\s?(.*)$", re.IGNORECASE)
                match = image_matcher.match(line)
                image_path = match.group(1)
                env_var_matcher = re.compile("\{\{[A-Z0-9_]*\}\}")
                if not env_var_matcher.match(image_path):
                    print("""Bad image found in {}. It is a direct reference instead of a templated reference: {}
                    Export images to resource.json to allow packaging for airgapped clusters.""".format(file, image_path))
                    bad_image = True

    return not bad_image


def print_help():
    print("""Scans a framework for any airgap issues. Checks all files for external URIs,
and docker images for direct references

usage: python airgap_linter.py <framework-directory>""")


def main(argv):
    if len(argv) < 2:
        print_help()
        sys.exit(0)

    framework_directory = argv[1]

    if not os.path.isdir(framework_directory):
        print("Supplied framework directory", framework_directory, "does not exist or is not a directory.")

    uris_valid = validate_all_uris(framework_directory)
    images_valid = validate_images(framework_directory)

    invalid = False
    if not uris_valid:
        invalid = True

    if not images_valid:
        invalid = True

    if invalid:
        print("Airgap check FAILED. This framework will NOT work in an airgap. Fix the detected issues.")
        sys.exit(1)

    print("Airgap check complete. This framework will probably work in an airgapped cluster, but for the love of everything test that.")
    sys.exit(0)


if __name__ == '__main__':
    sys.exit(main(sys.argv))
