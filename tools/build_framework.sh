#!/usr/bin/env bash

cat <<EOF
build_framework.sh has been replaced with build_package.sh.

Update your build.sh to use build_package.sh as follows. See dcos-commons/frameworks/hello-world/build.sh for an example:
- Before calling build_package.sh, your build.sh should explicitly invoke e.g. './gradlew check distZip' to build your scheduler.zip. See dcos-commons/frameworks/hello-world/build.sh for an example.
- Rename any '--artifact' flags to '-a'. In addition to any custom artifacts you may be building, you should now also provide the path to your scheduler.zip with e.g. '-a path/to/build/distributions/your-scheduler.zip'. See dcos-commons/frameworks/hello-world/build.sh for an example.
- Any BOOTSTRAP_DIR/EXECUTOR_DIR/CLI_DIR settings can be removed as they are not applicable to build_package.sh.
- If you are using build_go_exe.sh to build a binary across multiple platforms (e.g. to build a custom CLI across linux/darwin/windows), you should instead perform a single build_go_exe.sh invocation with multiple space-separated platforms. See dcos-commons/sdk/cli/build.sh for an example.

Additionally, if your service does not require a custom CLI with additional custom, you can now switch to a default CLI and forego the need to have golang to build your service.
Update your universe/resource.json's CLI entries as follows (with correct SHA256, SDKVERS and PLATFORM):
        "x86-64": {
          "contentHash": [
            {
              "algo": "sha256",
              "value": "SHA256: get output of 'curl https://downloads.mesosphere.com/dcos-commons/artifacts/SDKVERS/dcos-service-cli-PLATFORM | sha256sum'"
            }
          ],
          "kind": "executable",
          "url": "https://downloads.mesosphere.com/dcos-commons/artifacts/SDKVERS/dcos-service-cli-PLATFORM"
        }
If you do require a custom CLI (rare), then you must now explicitly build it within your build.sh and pass it to build_package.sh using '-a' arguments. See dcos-commons/frameworks/kafka/build.sh for an example.
EOF
exit 1
