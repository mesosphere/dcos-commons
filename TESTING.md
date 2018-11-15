# DC/OS SDK framework testing tools

## Static checking

In order to minimize errors in the system integration tests, static checks and code analysis tools are used on testing and utility code not explicitly exercised by unit tests. In order to make this process as simple and uniform as possible, the repo contains a number of helper scripts to run these tools. These same scripts are used by the build system to check the changes to the source code.

*Note*: These checks are only run against **modified** files relative to the target Git branch.

### Using pre-commit

The repository also contains a `.pre-commit-config.yaml` to be used with (`pre-commit`)[https://pre-commit.com/]. Once `pre-commit` is installed, the hooks can be added using
```bash
pre-commit install
```
in the project root.

### Python files

In order to run the checks against *modified* Python files, run the following from the root of the repository:
```bash
./tools/ci/steps/check_python_files.sh
```

The project uses (`black`)[https://github.com/ambv/black] formatting, and this is included in the `pre-commit` configuration. In order to format staged files run:
```bash
pre-commit run black-format
```

*Note*: This assumes that you have `pre-commit` installed.

## System integration tests

The `test.sh` script works with the `mesosphere/dcos-commons` Docker image to set up a reproducible test environment for testing any DC/OS SDK-based frameworks such as [dcos-commons](https://github.com/mesosphere/dcos-commons). In order to pull the testing utilities into a target framework, say `my-framework` in the current working folder, run:
```bash
docker run --rm -ti -v $(pwd):$(pwd) mesosphere/dcos-commons:latest init $(pwd)
```
This copies the files:
- `TESTING.md` (this document)
- `test.sh`
to the current working directory.

*Note:* If these files exist in the `$(pwd)` folder, they will be overwritten.

### Running tests
With the testing utilities in place, the tests for the framework(s) in the current folder can be run using:

```bash
CLUSTER_URL=$(dcos config show core.dcos_url) ./test.sh
```
This automatically detects the frameworks in the current folder and for each frameworks builds the framework and runs the integration tests.

*Note:* That this assumes that the DC/OS CLI has already been associated with a DC/OS cluster using the `dcos cluster setup` or `dcos cluster attach` commands.

#### Detection of frameworks
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
└── kafka

6 directories, 0 files
```
The detected frameworks would be:
- `cassandra`
- `elastic`
- `hdfs`
- `helloworld`
- `kafka`

* If no `frameworks` folder exists

If no `frameworks` folder exists, the current folder is assumed to be the root of a single framework. Note that the name of the framework displayed in this case (and above) is purely cosmetic, and the package name for the framework is determined from the catalog definition files.

#### Running tests interactively

The default when invoking `test.sh` is to run all tests defined for a given framework. It may, however be desirable to run a subset of tests interactively while developing a feature or the related tests themselves.

The following command:
```bash
./test.sh -i
```
starts an interactive shell in a Docker container with the requirements for running the tests installed. The folder from which `./test.sh` was started is mounted into the container as `/build` and the default (or specified) SSH key is mounted at `/ssh/key`.

**Note:** In order to run tests against a strict mode cluster and additional `-s` flag is required when starting the container.

##### Setting up
Before running test we perform the following steps inside the container:
* Connect to the cluster: `dcos cluster setup ${CLUSTER_URL}`
* Add the SSH key to the SSH agent: `eval $(ssh-agent -s); ssh-add /ssh/key`

It may also be required to set the `PYTHONPATH` environment variable:
```bash
export PYTHONPATH=/build/python
```

Most system integration tests rely on a stub-universe being set up allowing us to install the version of the framework under test. Thus it is required to run:
```bash
export STUB_UNIVERSE_URL="https://universe-converter.mesosphere.com/transform?url=https://infinity-artifacts.s3.amazonaws.com/autodelete7d/my-framework/20180806-125351-sdEdQ7mRfHFXQzmT/stub-universe-my-framework.json
```
The stub-universe URL can be optained from an earlier build, or by building the framework (`my-framework` in this case) in the container by running the following command:
```bash
./frameworks/my-framwork/build.sh aws
```
(where this assumes a multi-framework repository)
or simply:
```bash
./build.sh aws
```
for a single framework repository.

##### Interacting with tests
At this point, we are able to interactively run the tests using `py.test`. For example to check the tests available we could run we pass the `--collect-only` flag to the `py.test` command:
```bash
py.test -vv -s -m "sanity" --collect-only frameworks/my-framework/tests
```

If we're happy with the tests that will be run, we can drop the `--collect-only` flag and run the tests.

**Note:** The tooling included with SDK-based projects includes collecting task logs for failed tests. This is useful for CI jobs, but does add quite a bit of verbosity when running interactively. In order to disable this, run `export INTEGRATION_TEST_LOG_COLLECTION=False` before running the tests.

#### Advanced usage
Running
```bash
./test.sh --help
```
Will show all the options that are available for the `test.sh` script.
