# SDK Components

This directory contains most of the SDK bits that would be used in a running service. The contents of this directory are briefly described below.

## scheduler/, executor/, common/

- `scheduler/`: The main SDK Scheduler code. This is the "service manager" for SDK-based services, which handles keeping the service itself running.
- `executor/`: The 'custom executor', which is provided for compatibility with DC/OS 1.9 and older. DC/OS 1.10+ instead use a Default Scheduler provided by Mesos itself.
- `common/`: A Java library containing a small amount of code that's common between `scheduler/` and `executor/`.

## testing/

Java library containing utilities for writing unit tests which exercise services in a simulated environment. For examples, see [hello-world's unit tests](../frameworks/helloworld/src/test/java/com/mesosphere/sdk/helloworld/scheduler/ServiceTest.java).

## bootstrap/

The `bootstrap` utility for performing common tasks within the pods of SDK-based services.

To build, run `build.sh`. Note that no `GOPATH` is required, as an internal `GOPATH` is used for the build. The output will be a single linux build for use within DC/OS.

## cli/

The default service CLI module, used by the majority of services who don't add custom commands to their service CLIs. As of 0.40.0+, SDK releases include this default CLI, along with a SHA256SUMS file in the same directory for use in packaging.

Dependencies:
- Go 1.8+
- `../../cli` which contains the service CLI libraries.
- `../../govendor` which contains all vendored dependencies.

To build, run `build.sh`. Note that no `GOPATH` is required, as an internal `GOPATH` is used for the build. The output will include default CLIs for Linux, OSX, and Windows.
