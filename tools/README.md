# Build Tools

**WARNING: These tools are a continual work in progress and are likely to be changed and expanded over time.**

Common tools which automate the process of uploading, testing, and releasing DC/OS Services.

Build/release tools:

- **[build_package.sh](#build_packagesh)**: Given a universe template and a set of build artifacts to be uploaded, creates a 'stub universe' package, which is then optionally uploaded along with the provided artifacts to a dev S3 bucket (ideally a directory with expiration configured to 7d or so), or hosted in a local HTTP server.
- **[release_builder.py](#release_builderpy)**: Given an uploaded stub universe URL and a version string, transfers the artifacts to a more permanent 'release' S3 bucket and creates a PR against [Universe](https://github.com/mesosphere/universe/).

Misc utilities:

- **[dcos_login.py](#dcos_loginpy)**: Log into a DC/OS cluster using default/test credentials.
- **[print_package_tag.py](#print_package_tagpy)**: Return the Git repo SHA for the provided package on the cluster.
- **[universe_builder.py](#universe_builderpy)**: Underlying script which builds a `stub-universe.zip` given the necessary template data (package name, version, upload dir, ...). This is called by `publish_aws.py` and `publish_http.py` after autogenerating an upload directory.

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

The expected placeholder format is `{{sha256:yourfile.ext}}`, where `yourfile.ext` is one of the files which was passed to `publish_aws.py` or `publish_http.py`. Multiple files of the same name are not supported, as they all get uploaded to the same directory anyway. For example:

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

These parameters can then be filled in by passing a `TEMPLATE_CUSTOM_PLACEHOLDER` environment variable with the desired value when calling `publish_aws.py` or `publish_http.py`. You can learn more about this feature in the documentation for `publish_aws.py` and `publish_http.py`, below.

### Build script and artifacts

Ideally you should have some script in your project repository which defines how the project is built. This script would do any work to build the artifacts, then call `publish_aws.py` or `publish_http.py` with the paths to those artifacts provided so that they're uploaded along with the stub universe. This script can be then be instantiated by any CI that you might have.

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

### build_package.sh

Given a set of build artifacts, this utility will generate a stub universe against those artifacts, and upload the whole set to S3. This is useful for quickly getting a local build up and available for installation in a DC/OS cluster. This tool relies on `universe_builder.py`, which is described below.

The resulting uploaded stub universe URL is logged to stdout (while all other logging is to stderr).

Note that when using the `aws` publish method, the `aws` CLI must be present in the user's `PATH`.

#### Usage

```
$ ./build_package.sh <package-name> </abs/path/to/framework> [-a artifact1 -a artifact2 ...] [aws|local]
```

Example:

```
$ AWS_ACCESS_KEY_ID=devKeyId \
AWS_SECRET_ACCESS_KEY=devKeySecret \
S3_BUCKET=devBucket \
S3_DIR_PATH=dcosArtifacts/dev \
./build_package.sh \
    kafka \
    /path/to/dcos-commons/frameworks/kafka
    -a dcos-kafka-service/scheduler.zip \
    -a dcos-kafka-service/executor.zip \
    -a dcos-kafka-service/cli/dcos-kafka.exe \
    -a dcos-kafka-service/cli/dcos-kafka-dawin \
    -a dcos-kafka-service/cli/dcos-kafka-linux
    aws
[...]
---
Built and uploaded stub universe:
http://devBucket.s3.amazonaws.com/dcosArtifacts/dev/kafka/20160818-094133-hrjUfhcmQoznFVGP/stub-universe-kafka.zip

$ dcos package repo add --index=0 foo \
http://devBucket.s3.amazonaws.com/dcosArtifacts/dev/kafka/20160818-094133-hrjUfhcmQoznFVGP/stub-universe-kafka.zip
$ dcos package install kafka
[... normal usage from here ...]
```

For other examples of usage, take a look at `build.sh` for [Hello World](https://github.com/mesosphere/dcos-commons/blob/master/frameworks/helloworld/build.sh).

#### Environment variables

##### Common

- `TEMPLATE_<SOME_PARAM>`: Inherited by `universe_builder.py`, see below.
- `CUSTOM_UNIVERSES_PATH`: Text file to write the stub universe URL into

##### AWS Publish

Required:
- `AWS_ACCESS_KEY_ID`: AWS credential id (used by `aws`)
- `AWS_SECRET_ACCESS_KEY`: AWS credential secret (used by `aws`)

Optional:
- `S3_BUCKET` (default: `infinity-artifacts`): Name of the S3 bucket to use as the upload destination.
- `S3_DIR_PATH` (default: `autodelete7d`): Parent directory on the bucket to deposit the files within. A randomly generated subdirectory will be created within this path.
- `AWS_UPLOAD_REGION`: manual region to use for the S3 upload

##### Local HTTP Publish

Optional:
- `HTTP_DIR` (default: `/tmp/dcos-http-<pkgname>/`): Local path to be hosted by the HTTP daemon.
- `HTTP_HOST` (default: `172.17.0.1`, the IP used in dcos-docker): Host endpoint to be used by HTTP daemon.
- `HTTP_PORT` (default: `0` for an ephemeral port): Port to be used by HTTP daemon.
- `TEMPLATE_<SOME_PARAM>`: Inherited by `universe_builder.py`, see below.

### release_builder.py

Takes a Universe 2.x-format package built by `universe_builder.py`, copies its artifacts to a production S3 location, and automatically builds a Universe 3.x-format PR against [https://github.com/mesosphere/universe](Universe) which reflects the production location. After you've finished your testing and have a 'gold' build, use this to release to DC/OS.

The only needed parameters are a `stub-universe.zip` (built by `publish_aws.py`, or directly by `universe_builder.py`) and a version string to be used for the released package (eg `1.2.3-4.5.6`). This tool only interacts with build artifacts and does not have any dependency on the originating source repository.

Only artifacts which share the same directory path as the `stub-universe.zip` itself are copied. This allows for artifacts which are not built as a part of every release, but are instead shared across builds (e.g. a JVM package).

The resulting pull request URL is logged to stdout (while all other logging is to stderr).

Note that this utility is careful to avoid overwriting existing artifacts in production (ie if the provided version is already taken). If artifacts are already detected in the release destination, the program will exit and print the necessary `aws` command to manually delete the data.

#### Usage

```
$ ./release_builder.py \
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
- `RELEASE_UNIVERSE_REPO` (default: `mesosphere/universe`): The GitHub repository to submit the automated PR to.
- `RELEASE_BRANCH` (default: `version-3.x`): The target release branch for the automated PR.
- `DRY_RUN`: Refrain from actually transferring/uploading anything in S3, and from actually creating a GitHub PR.

## Misc Utilities

### dcos_login.py

Logs in the DC/OS CLI, effectively performing a complete `dcos auth login` handshake. On DC/OS Enterprise this assumes the initial `bootstrapuser` is still available, while on DC/OS Open this assumes that no user has yet logged into the cluster (when an initial testing token is automatically removed).

#### Usage

```
$ dcos config set core.dcos_url http://your-cluster-url.com
$ ./dcos_login.py [print]
[dcos CLI is now logged in...]
```

If the `print` argument is provided, `dcos_login.py` will also print the resulting token to stdout. All other log output is sent to stderr.

#### Environment variables

- `CLUSTER_URL`: Use the provided URL, instead of fetching `core.dcos_url` from a CLI.
- `CLUSTER_AUTH_TOKEN`: Use the provided auth token for `core.dcos_acs_token`, instead of attempting to fetch a token from the cluster. This is mainly for use by Open DC/OS clusters which are not newly created and have been logged into, at which point the default token used by `dcos_login.py` is no longer valid.

### print_package_tag.py

1. Looks up the latest package version available on the cluster.
2. Finds a tag of that name in the provided Git repository (url or local path).
3. Returns the SHA for that tag.

#### Usage

```
$ ./print_package_tag.py <package> [/local/repo/path or git@host.com:remote/repo]
```

Example (SHA from remote repository):

```
[dcos CLI must be logged into a cluster already...]
$ ./print_package_tag.py spark git@github.com:mesosphere/spark-build
238f921b65c2a0c5a6703d8400399aa09e084754
```

Example (SHA from local checkout of repository):

```
[dcos CLI must be logged into a cluster already...]
$ ./print_package_tag.py spark /local/path/to/spark-build/
238f921b65c2a0c5a6703d8400399aa09e084754
```

Example (just print the revision string):

```
[dcos CLI must be logged into a cluster already...]
$ ./print_package_tag.py spark
1.0.2-2.0.0
```

### github_update.py

Updates the correct GitHub PR with a status message about the progress of the build.
This is mainly meant to be invoked by CI during a build/test run, rather than being invoked manually by the developer. Outside of CI environments it just prints out the provided status.

#### Usage

```
$ ./github_update.py <state: pending|success|error|failure> <context_label> <status message>
```

Example:

```
$ GITHUB_TOKEN=yourGithubAuthToken \
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
- Disabling the notification to GitHub (eg in out-of-band tests where updating the repo doesn't make sense): non-empty `GITHUB_DISABLE`

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
$ ./universe_builder.py \
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

#### Enable Mount Volumes Script
```bash
$ virtualenv -p `which python3` py3env
$ source py3env/bin/activate
$ pip3 install fabric3
$ pip3 install boto3
$ export AWS_SECRET_ACCESS_KEY=SeCrEt_KeY
$ export AWS_ACCESS_KEY_ID=AcCeSs_Id
$ export STACK_ID=arn:aws:cloudformation:us-west-1:273854.....
$ ./tools/enable_mount_volumes.py
```
