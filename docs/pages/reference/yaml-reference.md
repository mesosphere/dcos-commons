---
title: YAML Reference
stylesheet: yaml-reference.css
redirect_from: "/yaml-reference"
---

<!-- {% raw %} disable mustache templating in this file: retain templated examples as-is -->

This reference document is a field-by-field listing of the YAML schema used for [Service Specifications](developer-guide.html#introduction-to-dcos-service-definitions). For an example of a real-world YAML Service Spec, see the [svc.yml for hello-world](https://github.com/mesosphere/dcos-commons/blob/master/frameworks/helloworld/src/main/dist/svc.yml). For several smaller examples, see the [SDK Developer Guide](developer-guide.html).

This documentation effectively reflects the Java object tree under [RawServiceSpec](api/?com/mesosphere/sdk/specification/yaml/RawServiceSpec.html), which is what's used as the schema to parse YAML Service Specifications. What follows is a field-by-field explanation of everything within that tree. For more information about service development in general, see the [SDK Developer Guide](developer-guide.html).

## Fields

* `name`

  The name of the service. This is used both for the Marathon app name for the scheduler, as well as for the Mesos framework name for the service tasks.

* `web-url`

  Where requests should be sent when a user goes to `http://theircluster.com/service/<name>` to view the service. By default this will go to the scheduler API endpoints. If you wish to expose additional custom endpoints via this URL, you should consider configuring [Reproxy](developer-guide.html#proxy-fallback) in your service so that the scheduler API endpoints are still available.

* `scheduler`

  This section contains settings related to the scheduler and its interaction with the cluster. All of these settings are optional, reasonable defaults are used if they are not manually provided.

  * `principal`

    The Mesos Principal to register as. Default is `<name>-principal`.

  * `zookeeper`

    Custom zookeeper URL for storing scheduler state. Defaults to `master.mesos:2181`.

  * `user`

    The system user to run the service's scheduler and pods as, with default `root` if unspecified. Availability of usernames depends on the cluster. In DC/OS Enterprise, if the security mode is set to strict, the service account used by the service must have permissions to launch tasks as the assigned user.

* `pods`

  This section contains a listing of all pod types managed by the service.

  * `resource-sets`

    Resource sets allow defining a single set of resources to be reused across multiple tasks, where only one task may use the resource set at a time. This can be useful when defining maintenance operations. A single resource set can be created, and then assigned to multiple operations such as backup, restore, rebuild, etc... In this scenario, only one operation may be active at a time, as that task has ownership of the resource set.

    * `cpus`, `gpus`, `memory`, `ports`, `volume`/`volumes`

      These resource values are identical in meaning to their sister fields in a [task definition](#tasks). However, see above discussion about these resources only being used by one task at a time.

  * `placement`

    Any additional constraints to be applied when deciding where to deploy this pod. This field supports all [Marathon placement operators](http://mesosphere.github.io/marathon/docs/constraints.html). For example, `[["hostname", "UNIQUE"]]` ensures that at most one pod instance is deployed per agent. This value may be exposed to end users via mustache templating to allow customizing placement of the service within their own environment.

  * `count`

    The number of pods of this type to be deployed. This may either be hardcoded or exposed to end users via mustache templating. This value may always be increased after the service has been deployed, but it can only be decreased if `allow-decommission` is `true`.

  * `allow-decommission`

    Whether to allow this pod's `count` to be decreased by an operator in a configuration update. For safety reasons this defaults to `false`, but the service developer may set this field to `true` to explicitly allow scale-down on a per-pod basis.

  * `image`

    The docker image to use for launching the pod, of the form `user/img:version`. The image may either be in public Docker Hub, or in a custom Docker Registry. Any custom Docker Registry must have been [configured in the DC/OS cluster](https://github.com/dcos/examples/tree/master/1.8/registry) to work. To ensure a lack of flakiness, docker images are only executed by Mesos' [Universal Container Runtime](https://docs.mesosphere.com/1.9/deploying-services/containerizers/), never `dockerd`. If this is unspecified, then a sandboxed directory on the system root is used instead.

    You do not have to specify an image if the service uses static binaries or an environment like the JVM to handle any runtime dependencies, but if your application requires a custom environment and/or filesystem isolation then you should probably specify an image here.

  * `rlimits`

    This section may be used to specify [rlimits](https://linux.die.net/man/2/setrlimit) that need to be configured (by Mesos) before the container is brought up. One or more rlimit values may be specified as follows:

    ```
    rlimits:
      RLIMIT_AS: // unlimited when 'soft' and 'hard' are both unset
      RLIMIT_NOFILE:
        soft: 128000
        hard: 128000
    ```

  * `secrets`

    This section list the Secrets that will be made available to the pod. The content of a Secret may be exposed as a file and/or as a environment variable.
    If Secret content is changed, relevant pod needs to be restarted, so it can update new content from the Secret store.

     * `secret`

         The path of a Secret. This setting would typically be configurable by service users as it must match what path the administrator used in the DC/OS Secrets UI.

     * `env-key`

         Name of the environment variable to expose the Secret content against. This may be left unset if the secret shouldn't be provided as an environment variable.

     * `file`

        A path within the container to copy the Secret content into. The Secret file is a tmpfs file; it disappears when executor exits.

  * `networks`

    Allows the pod to join any number of virtual networks on the DC/OS cluster. One kind of virtual network that is supported at present is the `dcos` overlay network. To have the pod join a virtual network (the `dcos` overlay network in this case) add the following to its YAML specification:

    ```
    networks:
      dcos:
    ```

    Pods on virtual networks have the following effects:

    <div class="noyaml"><ul>
    <li>The pod receives its own IP address from the subnet of the virtual network belonging to the agent where the pod is deployed. The IP can be retrieved using the DNS <code>&lt;task_name>.&lt;framework_name>.autoip.dcos.thisdcos.directory</code>. This DNS will also work for pods on the native host network.</li>
    <li>The <code>ports</code> resource requirements will be ignored (i.e. the agent does not need to have these ports available) because the pod has its own IP address.</li>
    <li>Once the pod is on a virtual network, you cannot move it to the host network. This is disallowed because the ports may not be available on the agent that has the rest of the task's reserved resources.</li>
    </ul></div>

    For more information see the [DC/OS Virtual Network documentation](https://docs.mesosphere.com/1.9/networking/virtual-networks/#virtual-network-service-dns).

  * `uris`

    A list of uris to be downloaded (and automatically unpacked) into the `$MESOS_SANDBOX` directory before launching instances of this pod. It is strongly recommended that all URIs be templated out and provided as scheduler environment variables. This allows field replacement in the case of running an offline cluster without internet connectivity.

    If you're using a Docker image (specified in the `image` field), these bits should ideally be already pre-included in that image, but separate downloads can regardless be useful in some situations.

    If you wish to use `configs` in your tasks, this needs to include a URI to download the `bootstrap` executable.

  * `volume`/`volumes`

    One or more persistent volumes to be mounted into the pod environment. These behave the same as volumes on a task or resource set, but are guaranteed to be shared between tasks in a pod. Although volumes defined on a task currently behave the same way, individual tasks will not be able to access volumes defined by another task in the future.

  * `pre-reserved-role`

    Ensures that this pod only consumes resources against a role which has already been statically assigned within the cluster. This is mainly useful for placing pods within a predefined quota, or otherwise assigning them a specific set of resources. For example, DC/OS clusters have a convention of using the `slave_public` role for machines which are not firewalled. Pods which have their `pre-reserved-role` set to `slave_public` will be placed on those machines so that they are visible outside the cluster.

  * `share-pid-namespace`

    Whether the tasks within this pod should share the same process id namespace (`true`), or whether pid namespaces should be distinct for every task in the pod (`false`). Default is `false`.

  * `tasks`

    This section lists the tasks which run within a given pod. All tasks share the same pod environment and resources. Resources may be more granularly allocated on a per-task basis in the future.

    * `goal`

      The goal state of the task. Must be either `RUNNING`, `FINISH` or `ONCE`:
      * `RUNNING`: The task should launch and continue running indefinitely. If the task exits, the entire pod (including any other active tasks) is restarted automatically. To demonstrate, let's assume a running instance the `hello-world` service on your DC/OS cluster. We'll be updating the configuration of the `hello-0` pod and verifying that the `hello-0-server` task with goal state `RUNNING` is restarted and stays running. First, we verify that the deploy plan has completed:
        ```
        $ dcos hello-world plan show deploy
        deploy (COMPLETE)
        ├─ hello (COMPLETE)
        │  └─ hello-0:[server] (COMPLETE)
        ...
        ```
        Now we take note of the ID of the `hello-0-server` task:
        ```
        $ dcos task
        NAME            HOST         USER   STATE  ID                                                    MESOS ID                                   REGION      ZONE
        hello-0-server  10.0.3.117  nobody    R    hello-0-server__46cf0925-9287-486b-83d7-7ffc43523671  61eee73c-b6a5-473c-990d-4bc8051cbd82-S4  us-west-2  us-west-2c
        ...
        ```
        Next, we update the amount of CPU being used by the `server` task in the `hello` pod type:
        ```
        $ echo '{"hello": {"cpus": 0.2}}' > options.json
        $ dcos hello-world update start --options=options.json
        ```
        After waiting for the update to complete and all tasks to be relaunched, we check the list of running tasks once again to verify that the `hello-0-server` step is complete, that the task was restarted (which we'll determine by verifying that it has a different task ID) and that it's still running:
        ```
        $ dcos hello-world plan show deploy
        deploy (COMPLETE)
        ├─ hello (COMPLETE)
        │  └─ hello-0:[server] (COMPLETE)
        ...
        $ dcos task
        NAME            HOST         USER   STATE  ID                                                    MESOS ID                                   REGION      ZONE
        hello-0-server  10.0.3.117  nobody    R    hello-0-server__3007283c-837d-48e1-aa0b-d60baead6f4e  61eee73c-b6a5-473c-990d-4bc8051cbd82-S4  us-west-2  us-west-2c
        ```
      * `FINISH`: The task should launch and exit successfully (zero exit code). If the task fails (nonzero exit code) then it is retried without relaunching the entire pod. If that task's configuration is updated, it is rerun. To demonstrate, let's assume that we've now launched hello-world with the `finish_state.yml` specfile, like so:
        ```
        $ echo '{"service": {"spec_file": "examples/finish_state.yml"}}' > options.json
        $ dcos package install --yes hello-world --options=options.json
        ```
        Once again, we wait for the deploy plan to complete, as above, and take note of the ID of the `world-0-finish` task (this time using the `--completed` flag, since the task has run to completion):
        ```
        $ dcos task --completed
        NAME            HOST         USER   STATE  ID                                                    MESOS ID                                   REGION      ZONE
        ...
        world-0-finish  10.0.0.232  nobody    F    world-0-server__955a28c2-d5bc-4ce4-a4e9-b9603784382e  61eee73c-b6a5-473c-990d-4bc8051cbd82-S3  us-west-2  us-west-2c
        ...
        ```
        Now we update the amount of CPU being used by the `finish` task in the `world` pod type:
        ```
        $ echo '{"world": {"cpus": 0.2}}' > options.json
        $ dcos hello-world update start --options=options.json
        ```
        After waiting for the update to complete, we check the task list again and this time see two completed entries for `world-0-finish`, showing that the configuration update has caused it to run to completion again:
        ```
        $ dcos task --completed
        NAME            HOST         USER   STATE  ID                                                    MESOS ID                                   REGION      ZONE
        ...
        world-0-finish  10.0.0.232  nobody    F    world-0-finish__955a28c2-d5bc-4ce4-a4e9-b9603784382e  61eee73c-b6a5-473c-990d-4bc8051cbd82-S3  us-west-2  us-west-2c
        world-0-finish  10.0.3.117  nobody    F    world-0-finish__bd03efc2-26a0-4e36-a332-38159492557e  61eee73c-b6a5-473c-990d-4bc8051cbd82-S4  us-west-2  us-west-2c
        ...
        ```
      * `ONCE`: The task should launch and exit successfully (zero exit code). If the task fails (nonzero exit code) then it is retried without relaunching the entire pod. If that task's configuration is updated, it will not be rerun. To demonstrate, let's assume that this time we've launched hello-world with the `discovery.yml` specfile, like so:</li>
        ```
        $ echo '{"service": {"spec_file": "examples/discovery.yml"}}' > options.json
        $ dcos package install --yes hello-world --options=options.json
        ```
        Again we wait for the deploy plan to complete and take note of the ID of the `hello-0-once` task, using the `--completed` flag since that task has run to completion:
        ```
        $ dcos task --completed
        NAME            HOST         USER   STATE  ID                                                    MESOS ID                                   REGION      ZONE
        ...
        hello-0-once  10.0.3.117  nobody    F    hello-0-once__8f167b23-48c8-4ea9-8559-4cf95a3703ae  61eee73c-b6a5-473c-990d-4bc8051cbd82-S4  us-west-2  us-west-2c
        ...
        ```
        Now we update the amount of CPU being used by both tasks in the `hello` pod type:
        ```
        $ echo '{"hello": {"cpus": 0.2}}' > options.json
        $ dcos hello-world update start --options=options.json
        ```
        After waiting for deployment to complete, we check the task list and find that `hello-0-once` only appears one time, indicating that the configuration update did not cause it to rerun:
        ```
        $ dcos task --completed
        NAME            HOST         USER   STATE  ID                                                    MESOS ID                                   REGION      ZONE
        ...
        hello-0-once  10.0.3.117  nobody    F    hello-0-once__8f167b23-48c8-4ea9-8559-4cf95a3703ae  61eee73c-b6a5-473c-990d-4bc8051cbd82-S4  us-west-2  us-west-2c
        ...
        ```

    * `essential`

      Marks this task as either "Essential", where task failure results in relaunching all tasks in the pod as a unit, or "Non-essential", where task failure results in only relaunching this task and leaving other tasks in the pod unaffected. By default this value is `true`, such that the failure of the task will result in relaunching all tasks in the pod. This is only applicable in cases where a given pod has multiple tasks with a goal state of `RUNNING` defined.

    * `cmd`

      The command to be run by the task, in the form of a shell script. This script may execute any executables that are visible within the pod environment.

      If you wish to use `configs` in this task, the `cmd` needs to run the `bootstrap` executable. For example: ```./bootstrap && ./your/exe/here```

    * `env`

      A listing of environment variables to be included in the `cmd` runtime. If you're using config templates using `bootstrap`, this section must be populated with any relevant template values.

      For convenience, the following environment variables are automatically provided to all tasks:
      <div class="noyaml"><ul>
      <li><code>TASK_NAME</code>: The name of the task, of the form <code>&lt;pod>-&lt;#>-&lt;task></code>. For example: <code>mypod-0-node</code>.</li>
      <li><code>FRAMEWORK_NAME</code>: The name of the service.</li>
      <li><code>FRAMEWORK_HOST</code>: The host domain for pods on the service. For example, the full hostname for a task would be <code>[TASK_NAME].[FRAMEWORK_HOST]</code>.</li>
      <li><code>FRAMEWORK_VIP_HOST</code>: The host domain for VIPs on the service. For example, the full hostname for a VIP would be <code>[VIP_NAME].[FRAMEWORK_VIP_HOST]</code>.</li>
      <li><code>SCHEDULER_API_HOSTNAME</code>: The hostname for the Scheduler HTTP API. For example, an endpoint on the scheduler would be <code>http://[SCHEDULER_API_HOSTNAME]/myendpoint</code>.</li>
      <li><code>POD_INSTANCE_INDEX</code>: The index of the pod instance, starting at 0 for the first instance.</li>
      <li><code>&lt;TASK_NAME>=true</code>: The task name as the envvar name, with <code>true</code> as the value.</li>
      </ul></div>

    * `configs`

      This section allows specifying config templates to be rendered by the `bootstrap` executable, which must be invoked manually in `cmd`. A common use case for DC/OS services is allowing end-users to customize the configuration of the service. This allows specifying arbitrary text templates which are automatically populated with that configuration. For example, say we had a `webserver` task with a `config.yaml` like the following:

      ```
      hostname: localhost
      port: 80
      ssl:
        enabled: true
        key: /etc/ssl/priv.key
        cert: /etc/ssl/pub.cert
      # Default value when unset:
      #custom_404: 404 Not Found
      #custom_403: 403 Forbidden
      root: /var/www
      ```

      The service developer can create a `config.yaml.mustache` which templates out the options to be exposed to end users:

      ```
      hostname: {{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos // mesos-dns hostname for this task
      port: {{PORT_HTTP}}
      ssl:
        enabled: {{SSL_ENABLED}}
        key: /etc/ssl/priv.key // not customizable
        cert: /etc/ssl/pub.cert // not customizable
      {{#CUSTOM_404_MESSAGE}}
      custom_404: {{CUSTOM_404_MESSAGE}}
      {{/CUSTOM_404_MESSAGE}}
      {{#CUSTOM_403_MESSAGE}}
      custom_403: {{CUSTOM_403_MESSAGE}}
      {{/CUSTOM_403_MESSAGE}}
      root: {{ROOT_DIR}}
      ```

      And then the following settings would be manually added to the task's `env`. These env vars meanwhile would be provided automatically by the SDK:
      <div class="noyaml"><ul>
      <li><code>TASK_NAME</code> and <code>FRAMEWORK_NAME</code> are included for free, as mentioned under <code>env</code> above.</li>
      <li><code>PORT_HTTP</code> is the default advertised environment variable for a reserved port named <code>http</code>, as mentioned under <code>ports</code> below.</li>
      </ul></div>

      ```
      env:
        SSL_ENABLED: {{WEB_SSL_ENABLED}}
        CUSTOM_404_MESSAGE: {{WEB_CUSTOM_404_MESSAGE}}
        CUSTOM_404_MESSAGE: {{WEB_CUSTOM_404_MESSAGE}}
        HTTP_ROOT: {{WEB_ROOT_DIR}}
      ```

      See the [SDK Developer Guide](developer-guide.html) more information on each of these files.

      * `template`

        The source template file path within the _scheduler_ environment to be downloaded into the task. Relative paths are interpreted as relative to the _scheduler's_ `$MESOS_SANDBOX`.

      * `dest`

        The destination path within the _task_ environment to place the rendered result. An absolute or relative path may be used. Relative paths are interpreted as relative to the _task's_ `$MESOS_SANDBOX`.

    * `cpus`

      The number of CPUs to be reserved by this task. Fractional values (e.g. `1.5`) are supported. If the task exceeds the reserved usage, it will be throttled and inconsistent performance may result.

    * `gpus`

      The number of GPUs to be reserved by this task. Unlike with CPUs this cannot be a fractional value in practice. This is only supported in DC/OS 1.9+.

    * `memory`

      The amount of RAM (in MB) to be reserved by this task. If the task exceeds this amount, it will be forcibly restarted.

    * `ports`

      The ports which your service will be using to accept incoming connections. Each port is given a unique name across the service, and this name is what's used to advertise that port in the `endpoints` listing. Ports may be defined a task as follows:

      ```
      ports:
        http-api:
          port: 0 # use a random port
          advertise: true # advertise the port in service endpoint lookups
          vip:
            port: 80 # create a VIP
        debug:
          port: 9090
          env-var: DEBUG_PORT # advertise DEBUG_PORT=9090 in task env
      ```

      All ports are reserved against the same interface that Mesos uses to connect to the rest of the cluster. In practice you should only use this interface as well. Surprising behavior may result if you use a different interface than Mesos does. For example, imagine dealing with a situation where Mesos loses connectivity on `eth0`, but your service is still connected fine over `eth1`. Or vice versa.

      It's worth noting that port reservations in DC/OS are technically honor-system at the moment. However, you should still reserve all the ports you intend to use. This is to ensure that Mesos doesn't place your task on a machine where a port you need is already occupied. You must give Mesos enough information to find a place where all your required ports are available.

      * `port`

        The port to be reserved and used by the service. This may be set to `0` to use a random port, which will be advertised via the task environment.

      * `env-key`

        This may be used to define an environment variable used to advertise this port within the task. This is most useful when a random dynamic port is being used, as it allows the task to know what port was allocated for it.

      * `advertise`

        This may be manually set to `true` to enable advertising this port in the service's `endpoints` listing. Default is `false`.

      * `vip`

        This section enables a Virtual IP (or VIP) address for this port. The VIP is effectively a highly-available hostname at which the task may be reached at an arbitrary advertised endpoint. Using VIPs is similar to using Mesos-DNS, except you have more control over the port used by others to connect to your service, without requiring users check SRV records like Mesos-DNS does. For example, you could run several web servers behind random ports (see above), but expose them all a single VIP endpoint at port `80`.

        * `port`

          The 'external' port to use in the VIP.

        * `prefix`

          The name to put at the start of the VIP. For example, `http` will result in a VIP hostname of `http.<servicename>.l4lb.thisdcos.directory`. By default, the parent port's name is used.

    * `health-check`

      Health checks are additional validation that your task is healthy, in addition to just the fact that its process is still running. This is an extra convenience for sitations where a service can enter a zombie state from which it can never return. For example, it might query an HTTP endpoint to validate that an HTTP service is still responding.

      * `cmd`

        This is the command to run in the health check. It will be run in the same environment as the task itself, but any envvars which are assigned _within_ the task's `cmd` will not appear here. If the command exits with code `0`, then the health check is considered successful. Otherwise it failed.

      * `interval`

        The period in seconds to wait after the last check has completed to start the next check.

      * `grace-period`

        An initial amount of time in seconds to ignore failed health checks.

      * `max-consecutive-failures`

        The number of consecutive health check failures which are allowed before the task is restarted. An unset value is treated as equivalent to no retries.

      * `delay`

        An amount of time in seconds to wait before starting the readiness check attempts. This delay is triggered once the task has started running.

      * `timeout`

        An amount of time in seconds to wait for a health check to succeed. If all health checks continuously fail for the timeout duration, the task is restarted (and its persistent volumes will persist).

    * `readiness-check`

      Readiness checks are similar in implementation to health checks, but they are only run when the task is first coming up. Readiness checks allow the service to expose when a given task has completed some initialization process, as opposed to just exposing that the process is running. If a readiness check is defined, the scheduler will wait until this check passes before attempting to launch another task. Unlike with health checks which are only really needed in specific cases, readiness checks are frequently useful for ensuring that process health during startup accurately represents the internals of the service, and to give the scheduler an opportunity to automatically restart a task if initialization is taking too long.

      * `cmd`

        This is the command to run in the readiness check. It will be run in the same environment as the task itself, but any envvars which are assigned _within_ the task's `cmd` will not appear here. If the command exits with code `0`, then the health check is considered successful. Otherwise it failed.

      * `interval`

        The period in seconds to wait after the last check has completed to start the next check.

      * `delay`

        An amount of time in seconds to wait before starting the readiness check attempts.

      * `timeout`

        An amount of time in seconds to wait for a readiness check to succeed. If all readiness checks continuously fail for the timeout duration, the task is restarted and initialization is reattempted.

    * `volume`/`volumes`

      One or more persistent volumes to be mounted into the task environment. Any files placed within persistent volumes will survive a task being restarted, but will _not_ survive a task being moved to a new machine. `volume` is a convenience syntax for specifying a task with a single volume.

      * `path`

        Where the persistent volume should be mounted in the task filesystem. A relative path will be placed relative to `$MESOS_SANDBOX`.

      * `type`

        Two types are currently supported: `ROOT` and `MOUNT`. Both behave the same in terms of persistence; the difference is mainly in how they perform and how they're reserved:
        <div class="noyaml"><ul>
        <li><code>ROOT</code> volumes are against the root filesystem of the host system. In terms of performance they will share IO with the other users of that filesystem. In terms of reservations, the requested size is exactly what's obtained.</li>
        <li><code>MOUNT</code> volumes are separate partitions which the cluster administrator had mounted onto the host machine as <code>/dcos/volumeN</code>. These partitions will typically have their own dedicated IO/spindles, resulting in more consistent performance. <code>MOUNT</code> volumes are reserved as a unit and are not shared across services. If a service requests a 1 GB volume and the <code>MOUNT</code> volumes are all 100 GB, then the service is getting a 100 GB volume all to itself.</li>
        </ul></div>

      * `size`

        The required minimum size of the volume. See reservation semantics between `ROOT` and `MOUNT` volume types above.

    * `resource-set`

      Tasks may either be assigned independent resources via the `cpus`, `gpus`, `memory`, `ports`, and `volume`/`volumes` fields, or they may be assigned to a common `resource-set` which was defined separately in `resource-sets` (see above). Not both.

    * `discovery`

      This may be used to define custom discovery information for the task, affecting how it's advertised in Mesos DNS.

      * `prefix`

        A custom name to use for advertising the pod via Mesos DNS. By default this is the pod name, so e.g. a pod specification named `foo` will by default have pod with discovery names of `foo-0`, `foo-1`, and so on.
        This value may be used to have pods whose hostnames are Mesos DNS (default `<podname>-<#>-<taskname>.<servicename>.mesos`) is different from their task name.
        Note that to avoid name collisions, different pods are not allowed to share the same prefix value.

      * `visibility`

        The default visibility for the discovery information. May be `FRAMEWORK`, `CLUSTER`, or `EXTERNAL`. If unset this defaults to `CLUSTER`. See [Mesos documentation](http://mesos.apache.org/documentation/latest/app-framework-development-guide/) on service discovery for more information on these visibility values.

    * `transport-encryption`

      A task may optionally ask for a X.509 TLS certificate with private key and CA certificate bundle. A certificate can be used by service to enable secure communication.

      * `name`

        A name of files representing the TLS artifacts in the task sandbox directory. For example a `name: nginx` with `type: TLS` will result in `$MESOS_SANDBOX/nginx.crt`, `$MESOS_SANDBOX/nginx.key` and `$MESOS_SANDBOX/nginx.ca` files.

      * `type`

        A type or format of delivered TLS artifacts.
        This can be set either to `TLS` for PEM encoded private key file, certificate and CA bundle or `KEYSTORE` for certificate and private key to be delivered in a separate keystore file and CA bundle in other truststore file.

      For detailed information see the [SDK Developer Guide](developer-guide.html#tls).

* `plans`

  This section allows specifying custom deployment behavior, either by replacing the default `deploy` plan, replacing the default `update` plan (otherwise `deploy` is used for updates), and/or by adding new custom plans. This can be useful for overriding the default behavior, which is sequentially deploying all the tasks in the order that they were declared above. Plans are listed in this section by name, with the content of each Plan listing the Phases and Steps to be run within them. See the [SDK Developer Guide](developer-guide.html#plans) for some examples and additional information on customizing Plans.

  * `strategy`

    How the phases within a given plan should be deployed, either `serial` or `parallel`. For example, a `serial` strategy will ensure Phase 1 is only stared after Phase 0 is complete, while a `parallel` strategy will start both Phase 0 and Phase 1 at the same time.

  * `phases`

    The list of Phases which compose a given Plan. In the canonical case of a deployment of separate `index` and `data` nodes, a Phase would represent deploying all of one of those types of nodes.

    * `strategy`

      How the steps within a given plan should be deployed. This may be any of `serial`, `parallel`, `serial-canary`, or `parallel-canary`. The `-canary` strategies will invoke the first step as a "trial", and then wait for the operator to manually confirm that the "trial" step was successful and invoke a `plan continue` call to continue the rollout. This may be useful in the case of deploying a configuration change to the cluster, where the first change is checked against a "canary" node before applying the rollout further.

    * `pod`

      The name of the pod (listed above) against which this phase will be invoked.

    * `steps`

      This section allows specifying non-default behavior for completing Steps. It may be used for e.g. defining custom init operations to be performed in the `deploy` plan, or for defining entirely custom plans for things like Backup and Restore. See the [SDK Developer Guide](developer-guide.html#plans) for some examples and additional information on specifying custom steps.
