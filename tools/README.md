# Build Tools

**WARNING: These tools are a continual work in progress and are likely to be changed and expanded over time.**

Common tools for building, testing, and releasing DC/OS Services.

These utilities perform individual tasks in a CI flow, but may also be used on the developer's local workstation.

To retrieve the latest version of these tools in automated environments, get a copy of the .zip release like so:

```
rm -rf dcos-commons-tools/ && curl https://infinity-artifacts.s3.amazonaws.com/dcos-commons-tools.tgz | tar xz
./dcos-commons-tools/universe_builder.py
```

### ci_upload.py

Given a set of build artifacts, this utility will generate a stub universe against those artifacts, and upload the whole set to S3. This is useful for calling in project build scripts to quickly get a build up and available for installation in a cluster. This is effectively a convenience wrapper around `universe_builder.py`, which is described below.

Note that this uses the `aws` CLI to perform the upload. You must have `aws` in your `PATH`.

### Usage

```
./ci_upload.py <package-name> <template-package-dir> [artifact files ...]
```

### Environment variables

- `S3_BUCKET`: Name of the S3 bucket to use as the upload destination.
- `S3_DIR_PATH`: Parent directory on the bucket to deposit the files within. A randomly generated subdirectory will be created within this path.
- `AWS_ACCESS_KEY_ID`: AWS credential id (used by `aws`)
- `AWS_SECRET_ACCESS_KEY`: AWS credential secret (used by `aws`)
- `AWS_UPLOAD_REGION`: manual region to use for the S3 upload (optional)
- `JENKINS_HOME`: Used to determine if a `$WORKSPACE/stub-universe.properties` file should be created (optional)
- `CUSTOM_UNIVERSES_PATH`: Text file to write the stub universe URL into (optional)
- `TEMPLATE_<SOME_PARAM>`: Inherited by `universe_builder.py`, see below.

## universe_builder.py

Builds a self-contained Universe 2.x-format package ('stub-universe') which may be used to add/test a given build directly on a DC/OS cluster.

The provided universe files may contain templates of the form `{{some-param}}`. The following parameters are filled by default:
- `{{package-version}}`: The version string to use for this package. Filled with the provided `package-version` argument
- `{{artifact-dir}}`: Where the artifacts are being uploaded to. Filled with the provided `artifact-dir` argument
- `{{sha256:somefile.txt}}`: Automatically populated with the sha256sum value of `somefile.txt`, whose location must have been passed as an argument.

In addition to these default template parameters, the caller may also provide environment variables of the form `TEMPLATE_SOME_PARAM` whose value will automatically be inserted into template fields named `{{some-param}}`. For example, running `TEMPLATE_DOCKER_IMAGE=mesosphere/docker-image ./universe_builder.py` would result in any `{{docker-image}}` parameters being replaced with `mesosphere/docker-image`.

### Usage

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
./universe_builder.py \
    kafka \
    1.2.3-4.5.6 \
    dcos-kafka-service/universe/ \
    https://example.com/path/to/kafka-artifacts \
    dcos-kafka-service/build/scheduler.zip \
    dcos-kafka-service/build/executor.zip \
    dcos-kafka-service/build/cli.zip
```

### Environment variables

As described above, any `TEMPLATE_<SOME_PARAM>` values will automatically be inserted into template slots named `{{some-param}}`. No other environment variables are needed.

## release_builder.py

Takes a Universe 2.x-format package built by `universe_builder.py`, copies its artifacts to a production S3 location, and automatically builds a PR against [Universe](https://github.com/mesosphere/universe).

The only needed parameters are a stub universe (built by `universe_builder.py`) and a version string to be used for the released package (eg `1.2.3-4.5.6`). This tool only interacts with build artifacts and does not have any dependency on the originating repository.

Note that this utility is careful to avoid overwriting existing artifacts in production (ie if the provided version is already taken). If artifacts are already detected in the production destination, the program will exit and provide the necessary `aws` command to manually delete the data.

### Usage

```
./release_builder.py \
    <package-version> \
    <stub-universe-url> \
    [commit message]
```

Example:

```
./release_builder.py \
    1.2.3-4.5.6 \
    https://example.com/path/to/stub-universe-kafka.zip \
    This is a test release
```

## github_update.py

Updates the correct GitHub PR with a status message about the progress of the build.
This is mainly meant to be invoked by CI during a build/test run, rather than being invoked manually by the developer. Outside of CI environments it just prints out the provided status.

### Usage

```
./github_update.py <state: pending|success|error|failure> <context_label> <status message>
```

Example:

```
./github_update.py pending build Building CLI
```

### Environment variables

Much of the environment detection logic is handled via environment variables:

- Detecting a CI environment: Non-empty `JENKINS_HOME`
- GitHub auth token: `GITHUB_TOKEN_REPO_STATUS`, or `GITHUB_TOKEN`
- git commit SHA: `ghprbActualCommit`, `GIT_COMMIT`, `${$GIT_COMMIT_ENV_NAME}}`, or finally by checking `git` directly.
- GitHub repository path: `GITHUB_REPO_PATH`, or by checking `git` directly.
- Detecting the link to include as a details link in the update: `GITHUB_COMMIT_STATUS_URL`, or `BUILD_URL/console`

Of these, `GITHUB_TOKEN` is the main one that needs to be set in a CI environment, while the others are generally autodetected.
Meanwhile `GITHUB_COMMIT_STATUS_URL` is useful for providing custom links in status messages.
