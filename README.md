Mesosphere DCOS Commons
======================

[![Build Status](https://jenkins.mesosphere.com/service/jenkins/buildStatus/icon?job=dcos-commons/infinity-dcos-commons-master)](https://jenkins.mesosphere.com/service/jenkins/job/dcos-commons/job/infinity-dcos-commons-master/)

This project is a collection of classes and utilities necessary for building a DCOS service.  It is written in Java and
is Java 1.8+ compatible. 

#### Prerequisites:
 - A workstation with 8G memory
 - Git
 - [VirtualBox](https://www.virtualbox.org/wiki/Downloads)
 - [Vagrant](https://www.vagrantup.com/downloads.html)`

1. `git clone -b pods https://github.com/mesosphere/dcos-commons.git`
2. `cd dcos-commons`
3. `./build-dcos-docker.sh`
4. Visit http://172.17.0.2/ to log into your local cluster.
5. `cd dcos-docker`
6. `vagrant ssh`
7. `cd /dcos-commons/frameworks/helloworld && ./build.sh local`
8. `dcos package install hello-world`
9. Visit http://172.17.0.2/#/services/%2Fhello-world/ to see your `hello-world` service running.

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
