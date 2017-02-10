# MongoDB sidecar
**Replica set functionality only**

This is MongoDB PoC replica set sidecar for DC/OS. All mongo replica set init/add/removal logic is a duty of sidecar application.

*Warning! Experimental quality software.*

## Sidecar
We use sidecar application along with default java dcos scheduler to simplify framework operations agains mongo.
Mongo binary is used for initialization of RS and [ruby](http://api.mongodb.com/ruby/current/) mongo driver to manage RS state and query for current status. RS members are stored in ZK at /dcos/frameworks/#{framework_name}/rs_members path.

## UI
Management interface is available at http://sidecar-0-companion.{{FRAMEWORK_NAME}}.mesos:4567

## YML specification
Framework is defined with [svc.yml](/frameworks/mongodb-sidecar/src/main/dist/svc.yml) specification. It defined tasks for mongo server nodes, sidecar companion, and deploy plan.

## Settings
Defaults settings can be changed in [config.json](/frameworks/mongodb-sidecar/universe/config.json) or can be overridden with json in command line or via UI installation.

{
  "mongodb": {
    "mem": 2048,
    "disk": 5000,
    "version": "3.2.11"
  }
}

## Build

## dcos-commons part:
To build mognodb sidecar
1. clone repo, checkout mongodb-sidecar branch
2. navigate to frameworks/mongodb-sidecar folder
3. AWS_PROFILE=profile S3_BUCKET=your-fancy-bucket TEMPLATE_PACKAGE_VERSION=x.y.z S3_DIR_PATH=frameworks ./build.sh aws

This will build mongodb-sidecar artifact and put it to provided s3 bucket. It will also display instruction how to add it to your DC/OS universe.


## ruby sidecar container
1. navigate to frameworks/mongodb-sidecar/src/main/docker
2. docker build -t dockerhub_account/mongodb-sidecar:x.y.z .
3. docker push dockerhub_account/mongodb-sidecar:x.y.z

You can override sidecar container version in svc.yml specification or in DC/OS ui.

## Limitations
* Downscaling is not possible at the moment.
* Removal mongo node from RS on task replacement should be done by hand with UI.
*

## TODO
* CNI
* Use ZK for CustomHook to store replace events.
* Create recovery plan to recreate replica set from known nodes.  
* Shakedown tests


## See also
* [mongodb-replicaset](https://github.com/tobilg/dcos-commons/) for DC/OS
* [mongo-k8s-sidecar](https://github.com/cvallance/mongo-k8s-sidecar)
* [flynn mongo](https://github.com/flynn/flynn/tree/master/appliance/mongodb).
