This directory serves to contain all relevant config files and script to enable the deployment and teardown of a
Kerberos Domain Controller (KDC) server.

The `kdc.json` config is used by the `sdk_auth` testing module to configure a KDC in the integration test environment.

The Dockerfile is used to maintain/build a docker image which serves the KDC. The `run.sh` and `kdc.conf` files faciliate the bootstrap for
said server as they're copied into the image via the Dockerfile recipe.

The `kdc.py` script is an ad-hoc tool to deploy/teardown a KDC instance outside of the testing environment. Its usage
from the root of the repo would look something like:
```
PYTHONPATH=testing ./tools/kdc.py deploy principals.txt
```

where `principals.txt` is a file of new-line separated strings of principals.
