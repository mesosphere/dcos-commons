import collections
import difflib
import os.path
import pytest
import json
import logging
import os
import fnmatch

log = logging.getLogger(__name__)


@pytest.mark.smoke
@pytest.mark.sanity
@pytest.mark.universe
@pytest.mark.parametrize("file_base", ["config", "package", "resource"])
def test_universe_file_formatting(file_base):
    framework_dir = os.path.dirname(os.path.dirname(__file__))
    path_list = list()
    for path, subdirs, files in os.walk(framework_dir):
        for name in files:
<<<<<<< HEAD
            filtered_path = fnmatch.filter([os.path.join(path, name)], "*universe*.json")
=======
            filtered_path = fnmatch.filter([os.path.join(path, name)], '*universe*.json')
>>>>>>> 117a1b9bef1731552fcb524dcb81672f3e478081
        if len(filtered_path) > 0:
            path_list.extend(filtered_path)
    for path in path_list:
        with open(path, "r") as source:
            raw_data = [l.rstrip("\n") for l in source.readlines()]
            formatted_data = [
                l
                for l in json.dumps(
<<<<<<< HEAD
                    json.loads("".join(raw_data), object_pairs_hook=collections.OrderedDict),
                    indent=2
=======
                    json.loads("".join(raw_data), object_pairs_hook=collections.OrderedDict), indent=2
>>>>>>> 117a1b9bef1731552fcb524dcb81672f3e478081
                ).split("\n")
            ]
            diff = list(
                difflib.unified_diff(raw_data, formatted_data, fromfile=path, tofile="formatted")
            )
            if diff:
                print("\n" + ("\n".join(diff)))
                pytest.fail("%s is not formatted correctly,see diff above" % path)
