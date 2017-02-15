#!/usr/bin/python

import sys
import os.path

# Add /testing/ to PYTHONPATH:
this_file_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.normpath(os.path.join(this_file_dir, '..', '..', '..', 'testing')))
