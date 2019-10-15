#!/usr/bin/env python

import collections
import difflib
import os.path
import json
import os
import fnmatch


cwd = os.getcwd()
framework_dir = os.path.dirname(os.path.dirname(cwd+'/'))
print(framework_dir)
path_list = list()
for path, subdirs, files in os.walk(framework_dir):
    for name in files:
        filtered_path = fnmatch.filter([os.path.join(path, name)], "*universe*.json")
        if len(filtered_path) > 0:
            print(filtered_path)
            path_list.extend(filtered_path)

for path in path_list:
    with open(path, "r") as source:
        raw_data = [l.rstrip("\n") for l in source.readlines()]
        formatted_data = [
            l
            for l in json.dumps(
                json.loads("".join(raw_data), object_pairs_hook=collections.OrderedDict),
                indent=2,
            ).split("\n")
        ]
        diff = list(
            difflib.unified_diff(raw_data, formatted_data, fromfile=path, tofile="formatted")
        )
        if diff:
            print("\n" + ("\n".join(diff)))
            print("{} is not formatted correctly, see diff above".format(path))
