# CLI Vendor hack

This is a workaround for the go tooling being confused by `frameworks/myframework/cli/` pointing into `cli/` when using vendoring.

Frameworks in dcos-commons/frameworks/ should create a 'vendor' symlink which points to here. In other words, a symlink at `frameworks/yoursvc/cli/dcos-yoursvc/vendor` should point to `clivendor/`.

This workaround only applies to frameworks which are colocated in the dcos-commons repository. External projects shouldn't need to do this.
