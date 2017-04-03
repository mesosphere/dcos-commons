---
title: YAML Reference
stylesheet: yaml-reference.css
---

This reference document is a field-by-field listing of the YAML schema used for [Service Specifications](developer-guide.html#introduction-to-dcos-service-definitions). For an example of a YAML Service Spec, see the [svc.yml for hello-world](https://github.com/mesosphere/dcos-commons/blob/master/frameworks/helloworld/src/main/dist/svc.yml).

This documentation effectively reflects the Java object tree under [RawServiceSpec](api/index.html?com/mesosphere/sdk/specification/yaml/RawServiceSpec.html), which is what's used as the schema to parse YAML Service Specifications. What follows is a field-by-field explanation of everything within that tree. For more information about service development in general, see the [SDK Developer Guide](developer-guide.html).

## Fields

* `name`

  TODO

* `web-url`

  TODO

* `scheduler`

  TODO

  * `role`

    TODO

  * `principal`

    TODO

  * `api-port`

    TODO

  * `zookeeper`

    TODO

  * `user`

    TODO

* `pods`

  TODO

  * `resource-sets`

    Resource sets allow defining a single set of resources to be reused across multiple tasks, where only one task may use the resource set at a time. This can be useful when defining maintenance operations. A single resource set can be created, and then assigned to multiple operations such as backup, restore, rebuild, etc... In this scenario, only one operation may be active at a time, as that task has ownership of the resource set.

    * `cpus`, `gpus`, `memory`, `ports`, `volume`/`volumes`

      These resource values are identical in function to their sister fields in a [task definition](#tasks).

  * `placement`

    TODO

  * `count`

    TODO

  * `container`

    TODO

    * `image-name`

      TODO

    * `networks`

      TODO

    * `rlimits`

      TODO

      * `soft`

        TODO

      * `hard`

        TODO

  * `image`

    TODO

  * `networks`

    TODO

  * `rlimits`

    TODO

    * `soft`

      TODO

    * `hard`

      TODO

  * `strategy`

    TODO

  * `uris`

    TODO

  * `tasks`

    TODO

    * `goal`

      TODO

    * `cmd`

      TODO

    * `env`

      TODO

    * `configs`

      TODO

      * `template`

        TODO

      * `dest`

        TODO

    * `cpus`

      TODO

    * `gpus`

      TODO

    * `memory`

      TODO

    * `ports`

      TODO

      * `port`

        TODO

      * `env-key`

        TODO

      * `vip`

        TODO

        * `port`

          TODO

        * `prefix`

          TODO

        * `protocol`

          TODO

        * `advertise`

          TODO

    * `health-check`

      TODO

      * `cmd`

        TODO

      * `interval`

        TODO

      * `grace-period`

        TODO

      * `max-consecutive-failures`

        TODO

      * `delay`

        TODO

      * `timeout`

        TODO

    * `readiness-check`

      TODO

      * `cmd`

        TODO

      * `interval`

        TODO

      * `delay`

        TODO

      * `timeout`

        TODO

    * `volume`/`volumes`

      TODO

      * `path`

        TODO

      * `type`

        TODO

      * `size`

        TODO

    * `resource-set`

      TODO

    * `discovery`

      TODO

      * `prefix`

        TODO

      * `visibility`

        TODO

  * `user`

    TODO

* `plans`

  TODO

  * `strategy`

    TODO

  * `phases`

    TODO

    * `strategy`

      TODO

    * `pod`

      TODO

    * `steps`

      TODO
