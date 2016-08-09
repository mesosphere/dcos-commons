# Build Tools

**WARNING: These tools are a continual work in progress and are likely to be changed and expanded over time.**

Common tools for building, testing, and releasing DC/OS Services.

## Python utilities

These utilities perform individual tasks in a CI flow. These are meant to be invoked by other scripts.

### github_update.py

Updates a GitHub PR with a status message. Meant to be invoked by CI during a build/test run.

#### Usage

TODO syntax

#### Environment variables

TODO

### universe_builder.py

Builds a self-contained Universe 2.x-format package ('stub-universe') which may be used to add/test a given build directly on a DC/OS cluster.

#### Usage

TODO sample flow

#### Environment variables

TODO

### release_builder.py

Takes a Universe 2.x-format package built by `universe_builder.py`, copies its artifacts to a production repository, and builds a PR against [Universe](https://github.com/mesosphere/universe).

#### Usage

TODO sample flow

#### Environment variables

TODO

## Shell scripts

These utilities perform CI flows, and invoke the above Python utilities. They started off as bash scripts but may be converted into Python scripts like the others.

### ci-upload.sh

Generates a random directory in S3, invokes `universe_builder.py` against that directory, and uploads the provided artifacts to that directory.

#### Usage

TODO sample flow

#### Environment variables

TODO

### ci-test.sh

Runs a set of integration tests against a stub-universe which had been uploaded by `ci-upload.sh`.

#### Usage

TODO sample flow

#### Environment variables

TODO
