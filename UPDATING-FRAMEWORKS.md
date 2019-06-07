# Updating a framework which uses the DC/OS Commons SDK

Any framework built using the [DC/OS Commons SDK](https://github.com/mesosphere/dcos-commons) should be updated regularly in order to make use of new features in the SDK or consume bugfixes.

The parts of the SDK consumed consist of:
* The SDK Java libraries including:
    * scheduler libraries
    * testing libraries
* SDK artifacts including:
    * The `bootstrap` utility
* CLI binaries for the three supported platforms
* Build tooling
* Testing utilities

This file describes the procedure for updating such framework to use the current SDK version.

## Preparation

If the framework repository has never been updated in the way described below, then the following changes may be required:

### Check `build.gradle`

Check that `build.gradle` in the project root contains the following dependencies in addition to any others required:
```
dependencies {
    compile "mesosphere:scheduler:${dcosSDKVer}"
    testCompile "mesosphere:testing:${dcosSDKVer}"
}
```
as well as the following entry in the `ext` specification:
```
ext {
    dcosSDKVer = "<CURRENT_SDK_VERSION>"
}
```
(where `<CURRENT_SDK_VERSION>` represents a version string such as `0.30.1`)

Older versions of `build.gradle` contained the following dependencies and no entry in the `ext` specification:
* `compile "mesosphere:scheduler:<CURRENT_SDK_VERSION>"`
* `testCompile "mesosphere:testing:<CURRENT_SDK_VERSION>"`

Although this is supported in the current upgrade path, it is recommended that these are changed to match the dependencies at the start of this section as this will result in a single line diff in the `build.gradle` file on update.

### Check the `universe/resource.json` file

#### URIs
In order to facilitate upgrades, the `universe/resource.json` file should contain the following entries in the `"uris"` section:
```json
"uris": {
    "...": "...",
    "bootstrap-zip": "https://downloads.mesosphere.com/dcos-commons/artifacts/{{dcos-sdk-version}}/bootstrap.zip",
    "...": "..."
}
```
Note the use of the `{{dcos-sdk-version}}` mustache template to replace an explicit version specification.

#### CLIs

In addition, if no custom CLI command are required, the `"cli"` section in the `universe/resource.json` can be replaced by:
```json
"cli":{
    "binaries":{
      "darwin":{
        "x86-64":{
          "contentHash":[ { "algo":"sha256", "value":"{{sha256:dcos-service-cli-darwin@https://downloads.mesosphere.com/dcos-commons/artifacts/{{dcos-sdk-version}}/SHA256SUMS}}" } ],
          "kind":"executable",
          "url":"https://downloads.mesosphere.com/dcos-commons/artifacts/{{dcos-sdk-version}}/dcos-service-cli-darwin"
        }
      },
      "linux":{
        "x86-64":{
          "contentHash":[ { "algo":"sha256", "value":"{{sha256:dcos-service-cli-linux@https://downloads.mesosphere.com/dcos-commons/artifacts/{{dcos-sdk-version}}/SHA256SUMS}}" } ],
          "kind":"executable",
          "url":"https://downloads.mesosphere.com/dcos-commons/artifacts/{{dcos-sdk-version}}/dcos-service-cli-linux"
        }
      },
      "windows":{
        "x86-64":{
          "contentHash":[ { "algo":"sha256", "value":"{{sha256:dcos-service-cli.exe@https://downloads.mesosphere.com/dcos-commons/artifacts/{{dcos-sdk-version}}/SHA256SUMS}}" } ],
          "kind":"executable",
          "url":"https://downloads.mesosphere.com/dcos-commons/artifacts/{{dcos-sdk-version}}/dcos-service-cli.exe"
        }
      }
    }
  }
```
Meaning that the CLIs for the templated `{{dcos-sdk-version}}` are used directly instead of building these separately.

## Updating

### Clean the current working directory

It is recommended that the update be performed in a **clean git repository**. Running the following commands should ensure this:

**NOTE**: This is a destructive operation

```bash
$ git checkout -b update-sdk-version-to-<NEW_SDK_VERSION>
$ git reset --hard HEAD
$ git clean -fdx
```

Now running `git status should yield:
```bash
$ git status
On branch update-sdk-version-to-<NEW_SDK_VERSION>
nothing to commit, working tree clean
```

### Perform the update

Assuming the `build.gradle` and `resource.json` files have been updated accordingly, the update to a specific version of the SDK can be performed as follows:
```bash
$ docker pull mesosphere/dcos-commons:latest
$ docker run --rm -ti -v $(pwd):$(pwd) mesosphere/dcos-commons:latest copy-files $(pwd) --update-sdk <NEW_SDK_VERSION>
```

Running a `git status` after this process should show something like:
```bash
$ git status
On branch update-sdk-version-to-0.41.0
Changes not staged for commit:
  (use "git add <file>..." to update what will be committed)
  (use "git checkout -- <file>..." to discard changes in working directory)

	modified:   TESTING.md
	modified:   UPDATING.md
	modified:   conftest.py
	modified:   test.sh
	modified:   testing/README.md
	[...]
	modified:   tools/validate_pip_freeze.py

no changes added to commit (use "git add" and/or "git commit -a")
```
Note that the update procedure could also *delete* unneeded files.

Check the differences in `build.gradle` and `tools/release_builder.py` to ensure that the `<NEW_SDK_VERSION>` is present in both files.

Now add the changes to version control using the required git commands (`git add`, `git rm`).

## Further steps

* See the SDK [release notes](https://github.com/mesosphere/dcos-commons/releases)
  and [change log](https://github.com/mesosphere/dcos-commons/blob/master/changelog.md) for any changes required when consuming the SDK.
* If the build process is heavily customized, it may be that additional changes will be required to the `build.sh` file in the repo.
* The API of the testing tools in `testing` could have changed, and any integration tests may need to be updated. Run `git diff testing` to check for any relevant changes.
