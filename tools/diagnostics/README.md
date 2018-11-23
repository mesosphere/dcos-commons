# Service Diagnostics Bundle

Script that gathers SDK-related service diagnostics artifacts.

## Artifacts Gathered

- `dcos service --completed --inactive --json` output
- `dcos $service describe` output
- `dcos $service pod status --json` output
- `dcos $service plan status $plan --json` output for all `$plan`s
- stdout and stderr log files for all pod tasks and their executors (even killed
  or finished ones) and scheduler tasks
- `https://$cluster/$service/v1/debug/offers` output
- `https://$cluster/$service/v1/configurations/$configuration_id` output for all
  `$configuration_id`s
- Output of base-tech specific diagnostics commands (e.g.: Cassandra's `nodetool
  status`, Elasticsearch's node stats, etc.)

## Known Limitations

- Doesn't currently work with Spark services since Spark is not an SDK service
- Doesn't currently work with crash-looping schedulers
- Doesn't currently create a compressed archive from the bundle directory

## Requirements

- Docker
- Network access to DC/OS cluster
- DC/OS 1.10+

## Usage

1. Download the latest version

   ```bash
   wget https://infinity-artifacts.s3.amazonaws.com/dcos-commons/diagnostics/latest/create_service_diagnostics_bundle.sh
   chmod +x create_service_diagnostics_bundle.sh
   ```

1. Run script against a service. In this case against a Cassandra service named
   `/prod/cassandra`

   ```bash
   ./create_service_diagnostics_bundle.sh --package-name=cassandra --service-name=/prod/cassandra
   ```

On the first run the script might take a few minutes to start because it
downloads a Docker image. After the image is downloaded subsequent runs start
instantly.

When the script runs you should see something like:
```
$ ./create_service_diagnostics_bundle.sh --package-name=cassandra --service-name=/prod/cassandra

Will create bundle for:
  Package:         cassandra
  Package version: 2.3.0-3.0.16
  Service name:    /prod/cassandra
  DC/OS version:   1.11.5
  Cluster URL:     https://my-dcos-cluster.com/

Proceed? [Y/n]:
```

Make sure the information looks correct and press ENTER.

When the script finishes running it will create a directory under your current
`$(pwd)/service-diagnostic-bundles` directory. You can see the bundle directory
name at the end of the script output. E.g.:
```
Created /service-diagnostic-bundles/my-dcos-cluster_prod__cassandra_20180912T142246Z
```

Optionally, zip the bundle directory so that it can be uploaded somewhere:
```
$ cd service-diagnostic-bundles
$ zip -r my-dcos-cluster_prod__cassandra_20180912T142246Z.zip my-dcos-cluster_prod__cassandra_20180912T142246Z
```

## Development

### Working with the shell script
The `./tools/diagnostics/create_service_diagnostics_bundle.sh` script runs a
Python script in a Docker container.

To make it to pick up local changes made to Python modules just run the
repository script:

1. `cd` to your dcos-commons repository directory
   ```bash
   cd /path/to/dcos-commons
   ```

1. Make changes to Python modules under `/path/to/dcos-commons/tools/diagnostics`

1. Run repository script
   ```bash
   ./tools/diagnostics/create_service_diagnostics_bundle.sh --package-name=cassandra --service-name=/prod/cassandra
   ```

### Releasing a new version

Requires AWS S3 credentials.

1. Push PR with changes to diagnostics code

1. Wait for PR to be merged to master

1. Push a new PR with a `VERSION` bump in
   `tools/diagnostics/create_service_diagnostics_bundle.sh` and a new
   `CHANGELOG.md` entry

   `tools/diagnostics/create_service_diagnostics_bundle.sh`:
   ```bash
   readonly VERSION='vx.y.z'
   ```

   `CHANGELOG.md`:
   ```markdown
   ## vx.y.z (YYYY-MM-DD) - Release title
   ### New features
       - foo (commit URL)
       - bar (commit URL)
   ### Bug fixes
       - baz (commit URL)
       - qux (commit URL)
   ### Improvements
       - quux (commit URL)
       - quuz (commit URL)
   ```

1. Wait for PR to be merged to master

1. Build and publish Docker image tagged with the desired version

   - With the [Jenkins job](https://jenkins.mesosphere.com/service/jenkins/view/Infinity/job/infinity-tools/job/release-tools/job/build-docker-image)

     Make sure to set `IMAGE_TAG` correctly. For example, if `VERSION` is
     `v1.0.0`, `IMAGE_TAG` should be `diagnostics-v1.0.0`.

     | Parameter          | Value                  |
     | ------------------ | ---------------------- |
     | `DOCKER_FILE_PATH` | Dockerfile             |
     | `DOCKER_ORG`       | mesosphere             |
     | `IMAGE_TAG`        | diagnostics-`$VERSION` |
     | `IMAGE_NAME`       | dcos-commons           |
     | `GITHUB_ORG`       | mesosphere             |
     | `GITHUB_REPO`      | dcos-commons           |
     | `GIT_REF`          | master                 |

   - Or manually

     ```bash
     git fetch upstream
     git stash
     git checkout master
     git reset --hard upstream/master
     docker build -t "mesosphere/dcos-commons:diagnostics-${VERSION}" .
     docker push "mesosphere/dcos-commons:diagnostics-${VERSION}"
     git checkout -
     git stash pop
     ```

1. Publish shell script (which will use the Docker image tagged with the same version)

   1. Version bucket

      ```bash
      aws s3 cp \
        --acl=public-read \
        tools/diagnostics/create_service_diagnostics_bundle.sh \
        "s3://infinity-artifacts/dcos-commons/diagnostics/${VERSION}/create_service_diagnostics_bundle.sh"
      ```

   1. Latest bucket

      ```bash
      aws s3 cp \
        --acl=public-read \
        tools/diagnostics/create_service_diagnostics_bundle.sh \
        "s3://infinity-artifacts/dcos-commons/diagnostics/latest/create_service_diagnostics_bundle.sh"
      ```
