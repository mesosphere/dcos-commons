Python utility which wraps a set of architecture-specific binaries.

This effectively allows us to use Binary CLI modules without breaking compatibility for DC/OS 1.7 users.

Assumptions:
- All binaries should be in a 'binaries' subdir under bin_wrapper/.
- Linux file should end in '-linux'
- MacOS file should end in '-darwin'
- Windows file should end in '.exe'

Configuration:
See declarations at top of setup.py.

Usage:
python bin_wrapper/__init__.py test1 test2 test3
