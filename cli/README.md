# CLI Module

**WARNING: This library has not yet settled on a stable API and may break at any time. For now we recommend using vendoring to avoid surprise breakages.**

Common tools and libraries for implementing DC/OS CLI Modules for services.

We recommend sticking to a 'thin client' model where possible, where the CLI is effectively a thin convenience wrapper around an HTTP API provided by your scheduler. This allows end-users to directly query those HTTP APIs (and develop tooling against them) without needing to (re)implement a lot of additional logic.

The following functionality is provided to cover the needs of most 'thin clients' in line with the above model:
- Standardized argument handling (via the [Kingpin](https://github.com/alecthomas/kingpin) library)
- DC/OS authentication support (reusing authentication provided to the CLI)
- Making HTTP GET/PUT/POST/DELETE calls to services
- Pretty-printing JSON responses

While the main DC/OS CLI is written in Python (and then compiled into a standalone binary), the CLI Modules developed using these tools are instead written directly in Go 1.5+. This is chosen to greatly simplify cross-compilation compared to Python. See the [example build-cli.sh](../frameworks/helloworld/cli/build-cli.sh) for an example which cross compiles Linux/Mac/Win binaries in a single script.

## Build

Prerequisites:
- git
- [Go 1.5+](https://golang.org/dl/)

First, get the code and set up the environment (edit `/YOUR/GOPATH` as needed):

```bash
export GOPATH=/YOUR/GOPATH # eg ~/go
export GO15VENDOREXPERIMENT=1 # only required if using Go 1.5. for Go 1.6+ this step can be skipped

git clone git@github.com:mesosphere/dcos-commons && cd dcos-commons
```

Now you may build the example CLI module for each targeted platform. Run build.sh from the framework root, e.g. for `helloworld`:

```bash
cd frameworks/helloworld
./build.sh # will build framework including dcos-hello-world.exe, dcos-hello-world-darwin, dcos-hello-world-linux
```

To run the compiled CLI (the example below uses Linux, pick the correct binary for your platform):

```bash
./cli/dcos-hello-world/dcos-hello-world-linux hello-world -h
```

## Develop

See the [example CLI module](../frameworks/helloworld/cli/) for an example of how your CLI module could be built. The provided example adds a set of custom commands on top of the standard set.

Like the example CLI module, your own code may simply access the CLI libraries provided here by importing `github.com/mesosphere/dcos-commons/cli`. Your CLI module implementation may pick and choose which standard commands should be included, while also implementing its own custom commands.

### Vendoring Dependencies

It is highly recommended that CLIs depending on these library files use [`govendor`](https://github.com/kardianos/govendor). This will allow the projects to be built against a specific, snapshotted version of this library and insulate them from build failures caused by breaking changes in the `master` branch of this repository.

To vendorise a CLI that depends on this project, (e.g. [`helloworld/cli`](/frameworks/helloworld/cli/)):

1. Install [govendor](https://github.com/kardianos/govendor).

1. Ensure the CLI folder is linked into your `$GOPATH`:

    ```bash
    cd frameworks/helloworld/cli
    ln -s $(pwd) $GOPATH/src/github.com/mesosphere/dcos-commons/frameworks/helloworld/cli
    ```

1. Initialise `govendor` metadata:

    ```bash
    govendor init
    ```

1. Fetch the CLI dependency (and any subpackages):

    ```bash
    govendor fetch github.com/mesosphere/dcos-commons/cli/\^
    ```

When building this project with a go version greater than 1.5 (`go build` must be run from the project's directory within the `$GOPATH`), these dependencies will be picked up automatically.

To later update the version of this dependency, simply run `fetch` again:

    ```bash
    govendor fetch github.com/mesosphere/dcos-commons/cli/\^
    ```

(See the [`govendor` README](https://github.com/kardianos/govendor) for further examples, including fetching a specific revision or tag of this code.)

### Direct execution

You may manually test calls to your executable the same way that the DC/OS CLI would call it. See `Run` below.

### Packaging

1. Upload the executables to a persistent store.
2. Generate SHA256 checksums for the executables, by running `sha256sum <file>` on Linux or OSX.
3. Update your package's `resources.json` to point to the URLs from step 1, and to reference the checksums generated in step 2.

## Run

When installed, the module is executed as a regular binary by the DC/OS CLI, with a predefined set of arguments mapping to what the module is called and to what the user entered. The CLI will call it as follows:

```bash
$ <exename> <modulename> [args]
```

So for example, if someone called `dcos kafka broker list`, the `dcos-kafka` CLI module would be run as `dcos-kafka kafka broker list` by the DC/OS CLI.

The inclusion of `modulename` (`kafka` in this example) as an argument allows reuse of a single CLI module binary across multiple installed modules. For example, the `kafka` and `confluent` packages are using the same underlying code for their CLI module, where the module just detects which branding to display via the `modulename`.
