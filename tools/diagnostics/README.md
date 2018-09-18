# Service Diagnostics Bundle

Provides facilities for gathering SDK-related service diagnostics artifacts.

## Dependencies
- Docker

## Usage

1. Download the latest version

   ```bash
   wget https://infinity-artifacts.s3.amazonaws.com/dcos-commons/diagnostics/v0.1.0/create_service_diagnostics_bundle.sh
   chmod +x create_service_diagnostics_bundle.sh
   ```

1. Run script against a service. In this case against a Cassandra service named `/prod/cassandra`

   ```bash
   ./create_service_diagnostics_bundle.sh cassandra /prod/cassandra
   ```

The first run might take a few minutes because the script downloads a Docker image. After the image is downloaded
subsequent runs start instantly.

When the script runs you should see something like:
```
$ ./create_service_diagnostics_bundle.sh cassandra /prod/cassandra

Will create bundle for:
  Package:         cassandra
  Package version: 2.3.0-3.0.16-rc3
  Service name:    /prod/cassandra
  DC/OS version:   1.11.5
  Cluster URL:     https://my-dcos-cluster.com/

Proceed? [Y/n]:
```

Make sure the information looks correct and press ENTER.

When the script finishes running it will create a directory under your current `$(pwd)/service-diagnostic-bundles`
directory. You can see the bundle directory name at the end of the script output. E.g.:
```
Created /service-diagnostic-bundles/my-dcos-cluster_prod__cassandra_20180912T142246Z
```

## Development

### Working with the shell script
The `./tools/diagnostics/create_service_diagnostics_bundle.sh` script runs Python scripts in a Docker container.

To make it to pick up local changes made to modules:

1. `cd` to your dcos-commons repository directory

   ```bash
   cd /path/to/dcos-commons
   ```

1. Add dcos-commons mount volume to Docker exec command

   ```bash
   -v "$(pwd)":/dcos-commons:ro \
   ```

1. Overwrite `SCRIPT_PATH` to point to `/dcos-commons` instead of `/dcos-commons-dist`

   ```bash
   readonly SCRIPT_PATH="/dcos-commons/tools/diagnostics/create_service_diagnostics_bundle.py"
   ```

   *TODO*: instead of doing all this manually make the script take a flag and do that automatically (e.g.
   `--dcos-commons-directory=/path/to/dcos-commons`)

### Publishing a new version

1. `cd` to your dcos-commons repository directory

   ```bash
   cd /path/to/dcos-commons
   ```

1. Commit `VERSION` bump in `tools/diagnostics/create_service_diagnostics_bundle.sh`

   ```bash
   readonly VERSION='vx.y.z'
   ```

1. Build a Docker image tagged with the desire version

   ```bash
   docker build -t "mpereira/dcos-commons:diagnostics-${VERSION}" .
   ```

1. Push Docker image tagged with the desire version

   ```bash
   docker push "mpereira/dcos-commons:diagnostics-${VERSION}"
   ```

1. Publish script (which will use the Docker image tagged with the same version)

   ```bash
   aws s3 cp \
     --acl=public-read \
     tools/diagnostics/create_service_diagnostics_bundle.sh \
     "s3://infinity-artifacts/dcos-commons/diagnostics/${VERSION}/create_service_diagnostics_bundle.sh"
   ```
