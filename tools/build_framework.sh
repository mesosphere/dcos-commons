#!/usr/bin/env bash

cat <<EOF
build_framework.sh has been replaced with build_package.sh.

Update your build.sh to call build_package.sh as follows:
- Before calling build_package.sh, your build.sh should explicitly invoke e.g. './gradlew check distZip' to build your scheduler.zip
- BOOTSTRAP_DIR/EXECUTOR_DIR/CLI_DIR can be removed from your build.sh as they are not used in build_package.sh
- Rename any instances of '--artifact' with '-a'
- You should now pass the path to the built scheduler.zip using e.g. '-a <PROJ_DIR>/build/distributions/your-scheduler.zip'

Additionally, if your service does not require a custom CLI (with custom commands), you can now switch to a default CLI and forego the need to have golang to build your service.
Update your universe/resource.json's CLI entries as follows (with correct SHA, SDKVERS and PLATFORM):
        "x86-64": {
          "contentHash": [
            {
              "algo": "sha256",
              "value": "SHA: e.g. 'curl https://downloads.mesosphere.com/dcos-commons/artifacts/SDKVERS/dcos-service-cli-PLATFORM | sha256'"
            }
          ],
          "kind": "executable",
          "url": "https://downloads.mesosphere.com/dcos-commons/artifacts/SDKVERS/dcos-service-cli-PLATFORM"
        }
If you do require a custom CLI (rare), then you must now explicitly build it within your build.sh and pass it to build_package.sh using '-a' arguments. For an example of this, see: github.com/mesosphere/dcos-commons/frameworks/kafka/build.sh
EOF
exit 1
