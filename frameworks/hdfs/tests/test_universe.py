import collections
import difflib
import os.path
import pytest
import json
import logging

log = logging.getLogger(__name__)


@pytest.mark.smoke
@pytest.mark.sanity
@pytest.mark.universe
@pytest.mark.parametrize("file_base", ["config", "package", "resource"])
def test_universe_file_formatting(file_base):
    framework_dir = os.path.dirname(os.path.dirname(__file__))
    path = os.path.join(framework_dir, "universe/%s.json" % file_base)
    with open(path, "r") as source:
        raw_data = [l.rstrip("\n") for l in source.readlines()]
        formatted_data = [
            l
            for l in json.dumps(
                json.loads("".join(raw_data), object_pairs_hook=collections.OrderedDict), indent=2
            ).split("\n")
        ]
        diff = list(
            difflib.unified_diff(raw_data, formatted_data, fromfile=path, tofile="formatted")
        )
        if diff:
            print("\n" + ("\n".join(diff)))
            pytest.fail("%s is not formatted correctly, see diff above" % path)
