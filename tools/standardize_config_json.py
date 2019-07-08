#!/usr/bin/env python3

import argparse
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

    diff = list(difflib.unified_diff(o.split("\n"), c.split("\n")))

    if len(diff) == 0:
        LOG.info("No changes")
    else:
        LOG.info("Applied diff:")
        LOG.info("\n".join(diff))


def process(service_config_json_path: str, sdk_tools_config: dict):
    contents = read_json_file(service_config_json_path)
    original = read_json_file(service_config_json_path)

    reordered = reorder_service(contents["properties"]["service"]["properties"])
    contents["properties"]["service"]["properties"] = reordered

    sections = sdk_tools_config.get("sections", {})
    for section_name, head_and_tail in sections.items():
        if section_name in contents["properties"]:
            head = head_and_tail.get("head")
            tail = head_and_tail.get("tail")
            properties = contents["properties"][section_name]["properties"]
            reordered = reorder(properties, head, tail, reorder_property)
            contents["properties"][section_name]["properties"] = reordered

    print_diff(original, contents)

    write_json_file(service_config_json_path, contents)

    return 0


def main(argv):
    parser = argparse.ArgumentParser(
        description="Standardizes the ordering of sections in SDK service JSON configuration files."
    )

    parser.add_argument(
        "--service-config-json",
        type=str,
        required=True,
        default=None,
        help="Path to the SDK service configuration file to be standardized (e.g. config.json)",
    )

    parser.add_argument(
        "--sdk-tools-config",
        type=str,
        required=False,
        default=None,
        help="Path to the SDK Tools configuration file (e.g. sdk-tools.json)",
    )

    args = parser.parse_args()

    service_config_json_path = args.service_config_json
    sdk_tools_config_path = args.sdk_tools_config

    if not os.path.isfile(service_config_json_path):
        LOG.info(
            "'%s' is not a file, was expecting an SDK service configuration file",
            service_config_json_path,
        )
        return 1

    if sdk_tools_config_path:
        if not os.path.isfile(sdk_tools_config_path):
            LOG.info(
                "'%s' is not a file, was expecting an SDK Tools configuration file",
                sdk_tools_config_path,
            )
            return 1

        LOG.info("Parsing SDK Tools configuration from '%s'", sdk_tools_config_path)
        sdk_tools_config = json.loads(read_file(sdk_tools_config_path))
    else:
        sdk_tools_config: dict = {}

    return process(service_config_json_path, sdk_tools_config.get("standardize_config_json", {}))


if __name__ == "__main__":
    sys.exit(main(sys.argv))
