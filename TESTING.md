# DC/OS SDK framework testing tools

The `test.sh` script works with the `mesosphere/dcos-commons` Docker image to set up a reproducible test environment for testing any DC/OS SDK-based frameworks such as [dcos-commons](https://github.com/mesosphere/dcos-commons). In order to pull the testing utilities into a target framework, say `my-framework` in the current working folder, run:
```bash
docker run --rm -ti -v $(pwd):$(pwd) mesosphere/dcos-commons:latest init $(pwd)
```
This copies the files:
- `TESTING.md` (this document)
- `test.sh`
to the current working directory.

*Note:* If these files exist in the `$(pwd)` folder, they will be overwritten.

## Running tests
With the test utilities in place, the tests for the framework(s) in the current folder can be run using:

```bash
CLUSTER_URL=$(dcos config show core.dcos_url) ./test.sh
```
This automatically detects the frameworks in the current folder and for each frameworks builds the framework and runs the integration tests.

*Note:* That this assumes that the DC/OS CLI has already been associated with a DC/OS cluster using the `dcos cluster setup` or `dcos cluster attach` commands.

### Detection of frameworks
As mentioned above, the `test.sh` script detects all the frameworks in the current folder. This is done with the following precednce:

* If a `frameworks` folder exists

The subfolders of the `frameworks` folder are considered frameworks. For example in the case of the `dcos-commons` repository:
```bash
tree --dirsfirst -L 1 frameworks
frameworks
├── cassandra
├── elastic
├── hdfs
├── helloworld
├── kafka
└── template

6 directories, 0 files
```
The detected frameworks would be:
- `cassandra`
- `elastic`
- `hdfs`
- `helloworld`
- `kafka`
- `template`

* If no `frameworks` folder exists

If no `frameworks` folder exists, the current folder is assumed to be the root of a single framework. Note that the name of the framework displayed in this case (and above) is purely cosmetic, and the package name for the framework is determined from the catalog definition files.

### Advanced usage
Running
```bash
./test.sh --help
```
Will show all the options that are available for the `test.sh` script.
