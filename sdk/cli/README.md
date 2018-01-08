# Default CLI

This directory contains the default CLI build, used by the majority of services who don't add custom commands to their service CLIs. As of Jan 2018, default CLI builds are uploaded with every SDK release, along with a SHA256SUMS file in the same directory.

Dependencies:
- Go 1.8+
- `../../cli` which contains the service CLI libraries
- `../../govendor` which contains all vendored dependencies.

To build, run `build.sh`. Note that no `GOPATH` is required, as an internal `GOPATH` is constructed for the build. The output will include default CLIs for Linux, OSX, and Windows.
