# Build Tools

**WARNING: These tools are a continual work in progress and are likely to be changed and expanded over time.**

Common tools which automate the process of uploading, testing, and releasing DC/OS Services.

Build/release tools:

- **[ci_upload.py](#ci_uploadpy)**: Given a universe template and a set of build artifacts to be uploaded, creates a 'stub universe' package and uploads it along with the provided artifacts to a dev S3 bucket (ideally a directory with expiration configured to 7d or so).
- **[release_builder.py](#release_builderpy)**: Given an uploaded stub universe URL and a version string, transfers the artifacts to a more permanent 'release' S3 bucket and creates a PR against [Universe](https://github.com/mesosphere/universe/).

Test tools:

- **[launch_ccm_cluster.py](#launch_ccm_clusterpy)**: Launches a DC/OS cluster using the Mesosphere-internal 'CCM' utility. (This is due to be removed or replaced soon.)
- **[run_tests.py](#run_testspy)**: Given a running DC/OS cluster, downloads and sets up a sandboxed CLI for that cluster and runs integration tests.

Misc utilities:

- **[dcos_login.py](#dcos_loginpy)**: Log into a DC/OS cluster using default/test credentials. Autodetects DC/OS Open vs DC/OS Enterprise.
- **[github_update.py](#github_updatepy)**: Update a GitHub PR status with the progress of a build. Used by the above scripts, and may be used in your own build scripts to provide a nicer CI experience in PRs.
- **[universe_builder.py](#universe_builderpy)**: Underlying script which builds a `stub-universe.zip` given the necessary template data (package name, version, upload dir, ...). This is called by `ci_upload.py` after autogenerating an upload directory.

These utilities are designed to be used both in automated CI flows, as well as locally on developer workstations.

## Packaging Quick Start

In order to use these tools to package your service, there are a few ingredients to be added to your service repository:

- [Package template with placeholders](#package-template-with-placeholders)
- [Build script and artifacts](#build-script-and-artifacts)
- [Release script](#release-script)

### Package template with placeholders

The package template is the only particularly formal requirement, while the rest is fairly ad-hoc.

There should be a directory in the project repository containing the `.json` files that you want to be put in the Universe. The directory would look something like this:

```
project-repo/universe/
  command.json
  config.json
  marathon.json.mustache
  package.json
  resource.json
```

It's recommended that you include this directory within the project repository alongside the service code, so that you are able to easily make any packaging changes in concert with code changes in the same commit. For example, adding a new user-facing configuration setting to the packaging while also adding the code which supports that setting.

Once you have populated the package template directory, you should update it to include the following placeholders:
- Artifact url placeholders
- Version label placeholder
- Binary CLI module SHA placeholders
- Custom placeholders (if any)

#### Artifact url placeholders

Any URLs to build artifacts, typically within `command.json` and/or `resource.json`, should be of the form `{{artifact-dir}}/artifact.ext`.

For example, instead of `http://example.com/artifacts/1.1/scheduler.zip`, you should just have `{{artifact-dir}}/scheduler.zip`. There should be no other directories specified in the path, just the placeholder followed by the filename. The `{{artifact-dir}}` placeholder will be dynamically updated to point to an upload location.

Note that this only applies to the files that you expect to be built, uploaded, and included in every release of your service. External resources that aren't included in the main build, such as icons, JVM packages, or external libraries should instead be uploaded to a fixed location, and their paths in the packaging should point to that location without any templating.

For some examples of this, take a look at `resource.json` for [Kafka](https://github.com/mesosphere/dcos-kafka-service/blob/master/universe/resource.json) or [Cassandra](https://github.com/mesosphere/dcos-cassandra-service/blob/master/universe/resource.json).

#### Version label placeholder

Any version labels should be replaced with a `{{package-version}}` placeholder. This may involve any documentation strings that mention the version, but in particular you should change the `version` value within your `package.json` to `{{package-version}}`:

Before:
```
  "version": "1.5.0",
```

After:
```
  "version": "{{package-version}}",
```

For some examples of this, take a look at `package.json` for [Kafka](https://github.com/mesosphere/dcos-kafka-service/blob/master/universe/package.json) or [Cassandra](https://github.com/mesosphere/dcos-cassandra-service/blob/master/universe/package.json).

#### Binary CLI module SHA placeholders

If you are providing binary CLI modules with your service, the tooling supports automatically populating the package template with any required `sha256sum` values.

The expected placeholder format is `{{sha256:yourfile.ext}}`, where `yourfile.ext` is one of the files which was passed to `ci_upload.py`. Multiple files of the same name are not supported, as they all get uploaded to the same directory anyway. For example:

Before:
```
  "x86-64":{
    "contentHash":[ { "algo":"sha256", "value":"6b6f79f0c5e055a19db188989f9bbf40b834e620914edc98b358fe1438caac42" } ],
    "kind":"executable",
    "url":"{{artifact-dir}}/dcos-kafka-linux"
  }
```

After:
```
  "x86-64":{
    "contentHash":[ { "algo":"sha256", "value":"{{sha256:dcos-kafka-linux}}" } ],
    "kind":"executable",
    "url":"{{artifact-dir}}/dcos-kafka-linux"
  }
```

For some examples of this, take a look at `resource.json` for [Kafka](https://github.com/mesosphere/dcos-kafka-service/blob/master/universe/resource.json) or [Cassandra](https://github.com/mesosphere/dcos-cassandra-service/blob/master/universe/resource.json).

#### Custom placeholders

You may include custom placeholders of the form `{{custom-placeholder}}` anywhere in your template. This can be useful for e.g. passing the path to a per-build docker tag, a randomly generated key, or anything else that you expect to change on a per-build basis.

These parameters can then be filled in by passing a `TEMPLATE_CUSTOM_PLACEHOLDER` environment variable with the desired value when calling `ci_upload.py`. You can learn more about this feature in the documentation for `ci_upload.py`, below.

### Build script and artifacts

Ideally you should have some script in your project repository which defines how the project is built. This script would do any work to build the artifacts, then call `ci_upload.py` with the paths to those artifacts provided so that they're uploaded along with the stub universe. This script can be then be instantiated by any CI that you might have.

We specifically recommend including this script in the project repository alongside your code. This will allow adding/modifying/removing artifacts from your build on a per-commit basis in lockstep with code changes, whereas a single external script would cause synchronization issues between branches which may each have different artifacts.

For some examples of instantiation, take a look at `build.sh` for [Kafka](https://github.com/mesosphere/dcos-kafka-service/blob/master/build.sh) or [Cassandra](https://github.com/mesosphere/dcos-cassandra-service/blob/master/build.sh).

#### Downloading the latest tools

Within your script, it could make sense to just grab the latest .tgz release of these tools like so:

```
rm -rf dcos-commons-tools/ && \
    curl https://infinity-artifacts.s3.amazonaws.com/dcos-commons-tools.tgz | tar xz
./dcos-commons-tools/universe_builder.py
```

This .tgz is automatically updated when there's a change to `master`.

### Release script

This is a fairly minimal detail, but it can't hurt to have some automation around the call to `release_builder.py`. For example, you could create a Jenkins parameterized build which calls this script. The parameters would include:

- Version label (required)
- Stub universe url (required)
- Release comment(s) (optional)

## Build/Release Tools

What follows is a more detailed description of what each utility does and how it can be used:

### ci_upload.py

Given a set of build artifacts, this utility will generate a stub universe against those artifacts, and upload the whole set to S3. This is useful for quickly getting a local build up and available for installation in a DC/OS cluster. This tool relies on `universe_builder.py`, which is described below.

The resulting uploaded stub universe URL is logged to stdout (while all other logging is to stderr).

Note that this uses the `aws` CLI to perform the upload. You must have `aws` installed in your `PATH` to use this.

#### Usage

```
./ci_upload.py <package-name> <template-package-dir> [artifact files ...]
```

Example:

```
$ AWS_ACCESS_KEY_ID=devKeyId \
AWS_SECRET_ACCESS_KEY=devKeySecret \
S3_BUCKET=devBucket \
S3_DIR_PATH=dcosArtifacts/dev \
./ci_upload.py \
    kafka \
    dcos-kafka-service/universe \
    dcos-kafka-service/scheduler.zip \
    dcos-kafka-service/executor.zip \
    dcos-kafka-service/cli/dcos-kafka.exe \
    dcos-kafka-service/cli/dcos-kafka-dawin \
    dcos-kafka-service/cli/dcos-kafka-linux

[...]
---
Built and uploaded stub universe:
http://devBucket.s3.amazonaws.com/dcosArtifacts/dev/kafka/20160818-094133-hrjUfhcmQoznFVGP/stub-universe-kafka.zip

$ dcos package repo add --index=0 foo \
http://devBucket.s3.amazonaws.com/dcosArtifacts/dev/kafka/20160818-094133-hrjUfhcmQoznFVGP/stub-universe-kafka.zip
$ dcos package install kafka
[... normal usage from here ...]
```

For other examples of usage, take a look at `build.sh` for [Kafka](https://github.com/mesosphere/dcos-kafka-service/blob/master/build.sh) or [Cassandra](https://github.com/mesosphere/dcos-cassandra-service/blob/master/build.sh).

#### Environment variables

Required:
- `AWS_ACCESS_KEY_ID`: AWS credential id (used by `aws`)
- `AWS_SECRET_ACCESS_KEY`: AWS credential secret (used by `aws`)

Optional:
- `S3_BUCKET` (default: `infinity-artifacts`): Name of the S3 bucket to use as the upload destination.
- `S3_DIR_PATH` (default: `autodelete7d`): Parent directory on the bucket to deposit the files within. A randomly generated subdirectory will be created within this path.
- `AWS_UPLOAD_REGION`: manual region to use for the S3 upload
- `WORKSPACE`: Set by Jenkins, used to determine if a `$WORKSPACE/stub-universe.properties` file should be created with `STUB_UNIVERSE_URL` and `STUB_UNIVERSE_S3_DIR` values.
- `CUSTOM_UNIVERSES_PATH`: Text file to write the stub universe URL into
- `TEMPLATE_<SOME_PARAM>`: Inherited by `universe_builder.py`, see below.
- `DRY_RUN`: Refrain from actually uploading anything to S3.

### release_builder.py

Takes a Universe 2.x-format package built by `universe_builder.py`, copies its artifacts to a production S3 location, and automatically builds a Universe 3.x-format PR against [https://github.com/mesosphere/universe](Universe) which reflects the production location. After you've finished your testing and have a 'gold' build, use this to release to DC/OS.

The only needed parameters are a `stub-universe.zip` (built by `ci_upload.py`, or directly by `universe_builder.py`) and a version string to be used for the released package (eg `1.2.3-4.5.6`). This tool only interacts with build artifacts and does not have any dependency on the originating source repository.

Only artifacts which share the same directory path as the `stub-universe.zip` itself are copied. This allows for artifacts which are not built as a part of every release, but are instead shared across builds (e.g. a JVM package).

The resulting pull request URL is logged to stdout (while all other logging is to stderr).

Note that this utility is careful to avoid overwriting existing artifacts in production (ie if the provided version is already taken). If artifacts are already detected in the release destination, the program will exit and print the necessary `aws` command to manually delete the data.

#### Usage

```
./release_builder.py \
    <package-version> \
    <stub-universe-url> \
    [commit message]
```

Example:

```
$ GITHUB_TOKEN=yourGithubAuthToken \
AWS_ACCESS_KEY_ID=yourAwsKeyId \
AWS_SECRET_ACCESS_KEY=yourAwsKeySecret \
MIN_DCOS_RELEASE_VERSION=1.7 \
S3_RELEASE_BUCKET=your-release-bucket.example.com \
HTTP_RELEASE_SERVER=https://your-release-web.example.com \
RELEASE_DIR_PATH=dcos/release \
./release_builder.py \
    1.2.3-4.5.6 \
    http://devBucket.s3.amazonaws.com/dcosArtifacts/dev/kafka/20160818-094133-hrjUfhcmQoznFVGP/stub-universe-kafka.zip \
    This is a test release

[... artifact files copied to https://your-release-web.example.com/dcos/release/1.2.3-4.5.6/ ... ]

---
Created pull request for version 1.2.3-4.5.6 (PTAL):
https://github.com/mesosphere/universe/pull/625
```

#### Environment variables

This tool requires the following environment variables in order to upload the release artifacts to S3, and to create the pull request against [Universe](https://github.com/mesosphere/universe):

- `GITHUB_TOKEN`: The GitHub [auth token](https://github.com/settings/tokens) for creating the PR against [Universe](https://github.com/mesosphere/universe)
- `AWS_ACCESS_KEY_ID`: Your AWS key ID suitable for passing to the `aws` CLI when uploading to `RELEASE_SERVER_S3`.
- `AWS_SECRET_ACCESS_KEY`: Your AWS key secret suitable for passing to the `aws` CLI.

The following are optional:

- `MIN_DCOS_RELEASE_VERSION` (default: 1.7): The value of `minDcosReleaseVersion` to use for the released package, or `0` to set no value. See [universe documentation](https://github.com/mesosphere/universe) for more details on this value.
- `S3_RELEASE_BUCKET` (default: `downloads.mesosphere.io`): The S3 bucket to upload the release artifacts into.
- `HTTP_RELEASE_SERVER` (default: `https://downloads.mesosphere.com`): The HTTP base URL for paths within the above bucket.
- `RELEASE_DIR_PATH` (default: `<package-name>/assets`): The path prefix within `S3_RELEASE_BUCKET` and `HTTP_RELEASE_SERVER` to place the release artifacts. Artifacts will be stored in a `<package-version>` subdirectory within this path.
- `DRY_RUN`: Refrain from actually transferring/uploading anything in S3, and from actually creating a GitHub PR.

## Test Tools

### launch_ccm_cluster.py

Launches a DC/OS cluster using the Mesosphere-internal 'CCM' tool, then prints the information about the launched cluster to stdout (all other logging is to stderr). This is not usable by the general public and will be going away or getting replaced soon.

#### Usage

```
./launch_ccm_cluster.py
```

Starts cluster with the env-provided configuration (see env vars). On success, returns zero and prints a json-formatted dictionary containing the cluster ID and URL.  All other log output is always sent to stderr, to ensure that stdout is not polluted by logs. In addition to this, if the script detects that it's running in a Jenkins environment, it will also write the cluster ID and URL to a `$WORKSPACE/cluster-$CCM_GITHUB_LABEL.properties` file. On failure, returns non-zero.

```
./launch_ccm_cluster.py stop <ccm_id>
```

Stops cluster with provided id, with up to `CCM_ATTEMPTS` attempts and `CCM_TIMEOUT_MINS` time waited per attempt. Returns 0 on success or non-zero otherwise.

```
./launch_ccm_cluster.py wait <ccm_id> <current_state> <new_state>
```

Waits for the cluster to switch from some current state to some new state (eg `PENDING` => `RUNNING`), with up to `CCM_TIMEOUT_MINS` time waited. Returns 0 on success or non-zero otherwise.

#### Environment variables

Common options:

- `CCM_AUTH_TOKEN` (REQUIRED): Auth token to use when querying CCM.
- `CCM_GITHUB_LABEL`: Label to use in Github CI, which is prefixed by `cluster:` (default `ccm`, for `cluster:ccm`)
- `CCM_ATTEMPTS`: Number of attempts to complete a start/stop operation (e.g. number of times to attempt cluster creation before giving up) (default `2`)
- `CCM_TIMEOUT_MINS`: Number of minutes to wait for a start/stop operation to complete before treating it as a failure (default `45`)
- `DRY_RUN`: Refrain from actually sending requests to the cluster (only partially works, as no fake response is generated) (default `''`)

Startup-specific options:

- `CCM_DURATION_MINS`: Number of minutes that the cluster should run (default: `60`)
- `CCM_CHANNEL`: Development channel to use (default: `testing/master`)
- `CCM_TEMPLATE`: AWS Cloudformation template to use in the above channel (default `ee.single-master.cloudformation.json`)
- `CCM_PUBLIC_AGENTS`: Number of public agents to start (default: `0`)
- `CCM_AGENTS`: Number of private agents to start (default: `1`)
- `CCM_AWS_REGION`: Region to start an AWS cluster in (default `us-west-2`)
- `CCM_ADMIN_LOCATION`: IP filter range for accessing the dashboard (default `0.0.0.0/0`)
- `CCM_CLOUD_PROVIDER`: Cloud provider type value to use (default `0`)
- `WORKSPACE`: Set by Jenkins, used to determine if a `$WORKSPACE/cluster-$CCM_GITHUB_LABEL.properties` file should be created with `CLUSTER_ID` and `CLUSTER_URL` values.

### run_tests.py

Runs [Shakedown](#https://github.com/dcos/shakedown/) or dcos-tests integration tests against a pre-existing cluster. Handles retrieving the latest DC/OS CLI binary and placing it into a directory sandbox, and setting it up for immediate use by the tests.

Returns zero on success, or non-zero otherwise.

#### Usage

Shakedown tests:

```
./run_tests.py shakedown http://your-dcos-cluster.com /path/to/your/shakedown/tests/
```

Shakedown tests, with preceding installation of library requirements:

```
./run_tests.py shakedown http://your-dcos-cluster.com /path/to/shakedown/tests/ /path/to/shakedown/requirements.txt
```

dcos-tests (Mesosphere-internal, deprecated in favor of Shakedown):

```
./run_tests.py dcos-tests http://your-dcos-cluster.com /path/to/your/tests/ /path/to/dcos-tests/repo/ "selected types (eg 'sanity or recovery')"
```

#### Environment variables

This utility calls `dcos_login.py` and `github_update.py`, so the environment variables used by those tools are inherited. For example, the `DCOS_TOKEN` environment variable may be assigned to authenticate against a DC/OS Open cluster which had already been logged into (at which point the default token used by `dcos_login.py` is no longer valid).

## Misc Utilities

### dcos_login.py

Logs in the DC/OS CLI, effectively performing a complete `dcos auth login` handshake. OnDC/OS Enterprise this assumes the initial `bootstrapuser` is still available, while on DC/OS Open this assumes that no user has yet logged into the cluster (when an initial testing token is automatically removed).

#### Usage

```
dcos config set core.dcos_url http://your-cluster-url.com
./dcos_login.py [print]
[dcos CLI is now logged in...]
```

If the `print` argument is provided, `dcos_login.py` will also print the resulting token to stdout. All other log output is sent to stderr.

#### Environment variables

- `DCOS_TOKEN`: Use the provided auth token for `core.dcos_acs_token`, instead of attempting to fetch a token from the cluster. This is mainly for use by Open DC/OS clusters which are not newly created and have been logged into, at which point the default token used by `dcos_login.py` is no longer valid.

### github_update.py

Updates the correct GitHub PR with a status message about the progress of the build.
This is mainly meant to be invoked by CI during a build/test run, rather than being invoked manually by the developer. Outside of CI environments it just prints out the provided status.

#### Usage

```
./github_update.py <state: pending|success|error|failure> <context_label> <status message>
```

Example:

```
GITHUB_TOKEN=yourGithubAuthToken \
GITHUB_COMMIT_STATUS_URL=http://example.com/detailsLinkGoesHere.html \
./github_update.py \
    pending \
    build \
    Building CLI
```

For other examples of usage, take a look at the `build.sh` for [Kafka](https://github.com/mesosphere/dcos-kafka-service/blob/master/build.sh) or [Cassandra](https://github.com/mesosphere/dcos-cassandra-service/blob/master/build.sh).

#### Environment variables

Much of the logic for detecting the CI environment is handled automatically by checking one or more environment variables:

- Detecting a CI environment: Non-empty `WORKSPACE`
- GitHub [auth token](https://github.com/settings/tokens): `GITHUB_TOKEN_REPO_STATUS`, or `GITHUB_TOKEN`
- git commit SHA: `ghprbActualCommit`, `GIT_COMMIT`, `${${GIT_COMMIT_ENV_NAME}}`, or finally by checking `git` directly.
- GitHub repository path: `GITHUB_REPO_PATH`, or by checking `git` directly.
- Detecting the link to include as a details link in the update: `GITHUB_COMMIT_STATUS_URL`, or `BUILD_URL/console`

Of these, `GITHUB_TOKEN` is the main one that needs to be set in a CI environment, while the others are generally autodetected.
Meanwhile `GITHUB_COMMIT_STATUS_URL` is useful for providing custom links in status messages.

### universe_builder.py

Builds a self-contained Universe 2.x-format package ('stub-universe') which may be used to add/test a given build directly on a DC/OS cluster. The resulting zip file's path is printed to stdout, while all other logging goes to stderr.

The provided universe files may contain template parameters of the form `{{some-param}}`. The following parameters are filled by default:
- `{{package-version}}`: The version string to use for this package. Filled with the provided `package-version` argument
- `{{artifact-dir}}`: Where the artifacts are being uploaded to. Filled with the provided `artifact-dir` argument
- `{{sha256:somefile.txt}}`: Automatically populated with the sha256sum value of `somefile.txt`. The paths to these files must be provided as arguments when invoking the builder.

In addition to these default template parameters, the caller may also provide environment variables of the form `TEMPLATE_SOME_PARAM` whose value will automatically be inserted into template fields named `{{some-param}}`. For example, running `TEMPLATE_DOCKER_IMAGE=mesosphere/docker-image ./universe_builder.py` would result in any `{{docker-image}}` parameters being replaced with `mesosphere/docker-image`.

A universe template is effectively a directory with the JSON files that you want to include in your Universe package, with template paramters provided in the above format. For some real-world examples of universe templates, take a look at [Kafka's](https://github.com/mesosphere/dcos-kafka-service/tree/master/universe/) or [Cassandra's](https://github.com/mesosphere/dcos-cassandra-service/tree/master/universe/) templates.

#### Usage

```
./universe_builder.py \
    <package-name> \
    <package-version> \
    <template-package-dir> \
    <artifact-dir> \
    [artifact files ...]
```

Example:

```
$ TEMPLATE_SOME_CUSTOM_STRING="this text replaces any instances of {{some-custom-string}}" \
./universe_builder.py \
    kafka \
    1.2.3-4.5.6 \
    dcos-kafka-service/universe/ \
    https://example.com/path/to/kafka-artifacts \
    dcos-kafka-service/build/scheduler.zip \
    dcos-kafka-service/build/executor.zip \
    dcos-kafka-service/build/cli.zip
```

#### Environment variables

As described above, any `TEMPLATE_<SOME_PARAM>` values will automatically be inserted into template slots named `{{some-param}}`. No other environment variables are needed.
