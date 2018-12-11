To build into a 'wheel' file for distribution, run:

$ cd python
$ ./bootstraph.sh
$ source .virtualenv/bin/activate
$ VERSION=<sdk-version> python3 setup.py bdist_wheel
$ deactivate

Where <sdk-version> could be (e.g.) 0.56.0, 0.55.0+snapshot, etc.

Notice how we use 0.55.0+snapshot instead of 0.55.0-SNAPSHOT.  Some external
tooling will need to do this translation before passing this version number in.
