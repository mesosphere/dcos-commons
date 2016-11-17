Mesosphere DCOS Commons
======================

[![Build Status](https://jenkins.mesosphere.com/service/jenkins/buildStatus/icon?job=dcos-commons/infinity-dcos-commons-master)](https://jenkins.mesosphere.com/service/jenkins/job/dcos-commons/job/infinity-dcos-commons-master/)

This project is a collection of classes and utilities necessary for building a DCOS service.  It is written in Java and
is Java 1.8+ compatible. 

#### Prerequisites:
 - A workstation with 8GB memory
 - Git
 - [VirtualBox](https://www.virtualbox.org/wiki/Downloads)
 - [Vagrant](https://www.vagrantup.com/downloads.html)`

1. `git clone -b pods https://github.com/mesosphere/dcos-commons.git`
2. `cd dcos-commons/ && ./build-dcos-docker.sh`
3. Visit http://172.17.0.2/ to view the cluster dashboard.
4. `cd dcos-docker/ && vagrant ssh`
5. `cd /dcos-commons/frameworks/helloworld/ && ./build.sh local`
6. `dcos package install hello-world`

Visit http://172.17.0.2/#/services/%2Fhello-world/ to see your `hello-world` service running, or run `dcos hello-world -h` to see available commands provided by `hello-world` CLI module.

Building dcos-commons
--------------------------

`./gradlew build`

Publishing locally
--------------------------

`./gradlew publishToMavenLocal`

Using dcos-commons
--------------------------

The releases are hosted at the Maven repository at `downloads.mesosphere.com`. A sample `build.gradle` is provided below.
```
repositories {
  // other repositories
  maven {
    url "http://downloads.mesosphere.com/maven/"
  }
}

dependencies {
  // other dependencies
  compile "mesosphere:dcos-commons:+"
}
```
