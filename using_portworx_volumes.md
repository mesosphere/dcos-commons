# Developer Guide

This guide will help you to write frameworks using the dcos-commons sdk with Portworx for persistent storage.

You can use the dcos-commons guide to write the base framework for you service. The guide is available here: https://mesosphere.github.io/dcos-commons/developer-guide.html

## Portworx volumes with dcos-commons
Currently the upstream dcos-commons framework supports only ROOT and MOUNT volumes for persistent storage.
This fork of dcos-commons has added support for using Portworx volumes so that tasks can seamlessly failover between nodes in
case of node failures. This is done by using the Docker Volume Driver Interface (DVDI) available with DCOS.

## Updating framework to use Portworx volumes
Once you have written a framework using the above guide, you can update the service spec to use Portworx volumes. This can be
done by updating the `volume.type` key in the service spec to use volumes of type `DOCKER`. 

The following additional keys also need to be provided under the `volume` key

| Key Name| Required| Default Value | Description|
|---------|---------|---------------|------------|
| docker_volume_driver| Yes| None| The docker volume driver to use. Only `pxd` is supported|
| docker_volume_name | Yes | None | The base name for the volume name. For example, if the name is specified as helloVolume, the volumes for the pods will be named helloVolume-0, helloVolume-1, etc|
| docker_driver_options | No | None | Additional comma separated options that can be passed in when creating the volume. The options that can be passed in through this are defined here: https://docs.portworx.com/scheduler/mesosphere-dcos/inline.html#inline-volume-spec

For example, in the hello world framework, the volume spec for the hello pod would look like the following:
```
volume:
  path: hello-container-path
  type: DOCKER
  docker_volume_driver: pxd
  docker_volume_name: {{HELLO_DOCKER_DRIVER_VOLUME_NAME}}
  docker_driver_options: {{{HELLO_DOCKER_DRIVER_OPTIONS}}}
  size: {{HELLO_DISK}}
```
In the above example `docker_volume_name` and `docker_driver_options` are being populated using the mustache template specified
in the `marathon.json.mustache` file for the framework. The mustache file in turn uses `config.json` to get these value from
the user during installation.

## HelloWorld Example
The HelloWorld example to use Portworx volumes can be found here:
https://github.com/portworx/dcos-commons/tree/px_1.1_0.30.1/frameworks/helloworld
