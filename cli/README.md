# CLI Module

**WARNING: This library has not yet settled on a stable API and may break at any time. For now we recommend using vendoring to avoid surprise breakages.**

Common tools and libraries for implementing DC/OS CLI Modules for services, used for adding custom commands to per-service CLI modules. For the default CLI build, see [sdk/](../sdk).

We recommend sticking to a 'thin client' model where possible, where the CLI is effectively a thin convenience wrapper around an HTTP API provided by your scheduler. This allows end-users to directly query those HTTP APIs (and develop tooling against them) without needing to (re)implement a lot of additional logic.

The following functionality is provided to cover the needs of most 'thin clients' in line with the above model:
- Standardized argument handling (via the [Kingpin](https://github.com/alecthomas/kingpin) library)
- DC/OS authentication support (reusing authentication provided to the CLI)
- Making HTTP GET/PUT/POST/DELETE calls to services
- Pretty-printing JSON responses

While the main DC/OS CLI is written in Python (and then compiled into a standalone binary), the CLI Modules developed using these tools are instead written directly in Go. This is done to greatly simplify cross-compilation compared to Python.

## Build

Prerequisites:
- git
- [Go 1.8+](https://golang.org/dl/)

First, get the code:

```bash
git clone git@github.com:mesosphere/dcos-commons && cd dcos-commons
```

Now you may build the example CLI module for each targeted platform. Run `build.sh` from the example CLI module directory:

```bash
cd sdk/cli/
./build.sh # will build dcos-service-cli.exe, dcos-service-cli-darwin, dcos-service-cli-linux
```

To run the compiled CLI (the example below uses Linux, pick the correct binary for your platform):

```bash
./dcos-service-cli-linux hello-world -h
```

## Develop

See the [default CLI module](../sdk/) for the default CLI module with no added commands. For an example of adding custom commands, see the [Kafka CLI module](../frameworks/kafka/cli).

Like the example CLI modules above, your own code may access the CLI libraries provided here by importing `github.com/mesosphere/dcos-commons/cli`. Your CLI module implementation may pick and choose which standard commands should be included, while also implementing its own custom commands.

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

Your CLIs must be uploaded as artifacts with your build, and included in your `resource.json`. The SDK tooling has support for automatically populating the needed SHA256 values from local builds, or from pregenerated SHA256SUMS files fetched over HTTP. For more details see the [tools/ README](../tools).

## Run

When installed, the module is executed as a regular binary by the DC/OS CLI, with a predefined set of arguments mapping to what the module is called and to what the user entered. The CLI will call it as follows:

```bash
$ <exename> <modulename> [args]
```

So for example, if someone called `dcos kafka broker list`, the `dcos-service-cli` CLI module would be run as `dcos-service-cli kafka broker list` by the DC/OS CLI.

The inclusion of `modulename` (`kafka` in this example) as an argument allows reuse of a single CLI module build across multiple packages. In particular this is used by the default CLI build which is shared across most packages.
