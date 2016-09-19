Mesosphere DCOS Commons
======================

[![Build Status](http://jenkins.mesosphere.com/service/jenkins/buildStatus/icon?job=infinity-dcos-commons)](http://jenkins.mesosphere.com/service/jenkins/job/infinity-dcos-commons/)

This project is a collection of classes and utilities necessary for building a DCOS service.  It is written in Java and
is Java 1.7 compatible. 

Building dcos-commons
--------------------------

1. `./gradlew build`

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
