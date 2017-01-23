# MongoDB sidecar
**Replica set functionality only**

This is MongoDB PoC replica set sidecar for DC/OS. All mongo replica set init/add/removal logic is a duty of sidecar application.
For lazy fellas who prefer to express dummy application logic with dumb

*Warning! Experimental quality software.*



## Sidecar
Uses mongo binary to execute init/add/remove commands against replica set. Current RS members and candidates for removal are stored in ZK and processed with each sidecar loop.

## YML specification
Framework behaviour defined with [svc.yml](/frameworks/mongodb-sidecar/src/main/dist/svc.yml) specification file. It provides description for constructing pods, resources, ports, deploy/recovery plans, phases and so on.

## Settings
You can change defaults in  [config.json](/frameworks/mongodb-sidecar/universe/config.json) or provide overrides with json in command line or via UI installation.

{
  "mongodb": {
    "mem": 2048,
    "disk": 5000,
    "version": "3.2.11"
  }
}

## Default framework
Default framework sends mongo agent permanent failures notifications to sidecar with
[CustomHook](frameworks/mongodb-sidecar/src/main/java/fwd/cloud/frameworks/mongodbsidecar/scheduler/CustomHook.java). [PodSpecsCannotShrink](sdk/scheduler/src/main/java/com/mesosphere/sdk/config/validate/PodSpecsCannotShrink.java) validation were removed from default service specification to experiment with downscaling.

## Repository
Testing builds distributed to S3 and can be listed with  
`aws s3 ls khufu-dcos-stub-universe/autodelete7d/mongodb-replicaset/`

You should provide full path to mongodb-sidecar zip package when adding to DC/OS.

`https://khufu-dcos-stub-universe.s3.amazonaws.com/autodelete7d/mongodb-sidecar/20170119-152649-vt6fGhDm
pm5Hz4il/stub-universe-mongodb-sidecar.zip`


## TEST ME!


## TODO
* Use ZK for CustomHook to store replace events.
* Create recovery plan to recreate replica set from known nodes.  
* Decide how to distribute artifacts https://serv.sh/ or company S3.
* CNI
* Shakedown tests


## See also
* [mongodb-replicaset](https://github.com/tobilg/dcos-commons/) for DC/OS
* [mongo-k8s-sidecar](https://github.com/cvallance/mongo-k8s-sidecar)
* [flynn mongo](https://github.com/flynn/flynn/tree/master/appliance/mongodb).
