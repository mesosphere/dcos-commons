#!/usr/bin/env python3

usage = """Standardizes the ordering of sections in config.json files.

Usage:
$ ./standardize_config_json.py $path_to_config_json $path_to_dcos_commons_json"""

import collections
import difflib
import json
import logging
import os.path
import sys


logging.basicConfig(
    format="[%(asctime)s|%(name)s|%(levelname)s]: %(message)s", level="INFO", stream=sys.stdout
)

LOG = logging.getLogger(__name__)


def read_file(file_path: str) -> str:
    LOG.info("Reading from %s", file_path)
    with open(file_path, "r") as handle:
        return handle.read()


def read_json_file(file_path: str) -> collections.OrderedDict:
    return json.loads(read_file(file_path), object_pairs_hook=collections.OrderedDict)


def write_file(file_path: str, content: str) -> str:
    LOG.info("Writing to %s", file_path)
    with open(file_path, "w") as handle:
        handle.write(content)
        if not content.endswith("\n"):
            handle.write("\n")


def write_json_file(file_path: str, content: collections.OrderedDict):
    write_file(file_path, json.dumps(content, indent=2))


def reorder(
    original: collections.OrderedDict, head: list = [], tail: list = [], mapper=lambda x: x
) -> collections.OrderedDict:
    remaining = []

    if not isinstance(original, dict):
        return original

    for p in original.keys():
        if p in tail:
            continue
        if p in head:
            continue
        remaining.append(p)

    reordered = collections.OrderedDict()
    for p in head:
        if p in original:
            reordered[p] = mapper(original[p])

    for p in sorted(remaining):
        reordered[p] = mapper(original[p])

    for p in tail:
        if p in original:
            reordered[p] = mapper(original[p])

    return reordered


def reorder_property(schema: collections.OrderedDict) -> collections.OrderedDict:
    return reorder(schema, head=["description", "type", "enum", "default"], tail=["properties"])


def reorder_service(service_properties: collections.OrderedDict) -> collections.OrderedDict:
    expected_order_head = [
        "name",
        "user",
        "service_account",
        "service_account_secret",
        "virtual_network_enabled",
        "virtual_network_name",
        "virtual_network_plugin_labels",
        "mesos_api_version",
        "log_level",
    ]

    expected_order_tail = ["security"]

    return reorder(service_properties, expected_order_head, expected_order_tail, reorder_property)


def print_diff(original: collections.OrderedDict, new: collections.OrderedDict):
    o = json.dumps(original, indent=2)
    c = json.dumps(new, indent=2)

    diff = difflib.unified_diff(o.split("\n"), c.split("\n"))

    LOG.info("\n".join(diff))


def process(config_json_path: str, configuration: list):
    contents = read_json_file(config_json_path)
    original = read_json_file(config_json_path)

    reordered = reorder_service(contents["properties"]["service"]["properties"])
    contents["properties"]["service"]["properties"] = reordered

    for section_name, head_and_tail in configuration["sections"].items():
        head = head_and_tail["head"]
        tail = head_and_tail["tail"]
        properties = contents["properties"][section_name]["properties"]
        reordered = reorder(properties, head, tail, reorder_property)
        contents["properties"][section_name]["properties"] = reordered

    print_diff(original, contents)

    write_json_file(config_json_path, contents)


if __name__ == "__main__":
    args = sys.argv[1:]

    if args[0] in set(["-h", "-help", "--help"]):
        print(usage)
        exit(0)

    if len(args) > 2:
        print(usage)
        exit(0)

    config_json_path = args[0]

    if not os.path.isfile(config_json_path):
        LOG.info("'%s' is not a file, was expecting a config.json file", config_json_path)

    configuration_path = None
    configuration = None
    LOG.info("config_json_path: %s", config_json_path)

    if len(args) == 2:
        configuration_path = args[1]

    LOG.info("configuration_path: %s", configuration_path)

    if configuration_path:
        if not os.path.isfile(configuration_path):
            LOG.info(
                "'%s' is not a file, was expecting a dcos_commons.json file", configuration_path
            )

        LOG.info("Parsing dcos-commons tooling configuration from '%s'", configuration_path)
        configuration = json.loads(read_file(configuration_path))

    process(config_json_path, configuration and configuration.get("standardize_config_json"))
