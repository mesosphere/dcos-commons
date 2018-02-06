"""
A simple script to ensure that the ordering of the "service" section in config.json is standardized.
NOTE: This overwrites the files.

Usage: from the `dcos-commons` root:

$ python tools/update_config_json.py

"""
import collections
import json
import logging
import sys
import difflib


logging.basicConfig(
    format='[%(asctime)s|%(name)s|%(levelname)s]: %(message)s',
    level="INFO",
    stream=sys.stdout)

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


def reorder(original: collections.OrderedDict, head: list=[], tail: list=[],
            mapper=lambda x: x) -> collections.OrderedDict:
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

    for p in remaining:
        reordered[p] = mapper(original[p])

    for p in tail:
        if p in original:
            reordered[p] = mapper(original[p])

    return reordered


def reorder_property(schema: collections.OrderedDict) -> collections.OrderedDict:
    return reorder(schema, head=["description", "type", "enum", "default", ], tail=["properties", ])


def reorder_service(service_properties: collections.OrderedDict) -> collections.OrderedDict:
    expected_order_head = ["name",
                           "user",
                           "service_account",
                           "service_account_secret",
                           "virtual_network_enabled",
                           "virtual_network_name",
                           "virtual_network_plugin_labels",
                           "mesos_api_version",
                           "log_level", ]

    expected_order_tail = ["security", ]

    return reorder(service_properties,
                   expected_order_head, expected_order_tail,
                   reorder_property)


def print_diff(original: collections.OrderedDict, new: collections.OrderedDict):
    o = json.dumps(original, indent=2)
    c = json.dumps(new, indent=2)

    diff = difflib.unified_diff(o.split("\n"), c.split("\n"))

    LOG.info("\n".join(diff))


def process(filename: str):
    contents = read_json_file(filename)
    original = read_json_file(filename)

    reordered = reorder_service(contents["properties"]["service"]["properties"])
    contents["properties"]["service"]["properties"] = reordered

    print_diff(original, contents)

    write_json_file(filename, contents)


if __name__ == "__main__":
    files = [
        "frameworks/cassandra/universe/config.json",
        "frameworks/elastic/universe-kibana/config.json",
        "frameworks/elastic/universe/config.json",
        "frameworks/hdfs/universe/config.json",
        "frameworks/helloworld/universe/config.json",
        "frameworks/kafka/universe/config.json",
        "frameworks/template/universe/config.json",
        ]

    for f in files:
        process(f)
