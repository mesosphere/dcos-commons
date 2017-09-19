# Go vendor hack

This directory contains the dependencies for both the service CLIs, and for the `bootstrap` utility.

This is a workaround for the Go tooling being confused by e.g. `github.com/mesosphere/dcos-commons/frameworks/myframework/cli/` pointing into `github.com/mesosphere/dcos-commons/cli/` when using vendoring.

Frameworks in `dcos-commons/frameworks/` should create a `vendor` symlink which points to here. In other words, a symlink at `frameworks/yoursvc/cli/dcos-yoursvc/vendor` should point to `govendor/`.

This workaround only applies to frameworks which are colocated in the `dcos-commons` repository. External projects shouldn't have to do this, and can instead have a regular `vendor` directory structure with a copy of `github.com/mesosphere/dcos-commons/cli`.
