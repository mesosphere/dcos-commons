---
title: Kafka Tutorial
---

<!-- {% raw %} disable mustache templating in this file: retain templated examples as-is -->

In this tutorial we'll be walking through step-by-step instructions on getting a Kafka service up and running. This tutorial assumes you've already setup a local cluster using the [Quick Start](https://github.com/mesosphere/dcos-commons/blob/master/README.md) guide and that you're in the VM environment.

#### Steps:

1. Change directory into `dcos-commons` and create a new service by running: `cd /dcos-commons && ./new-framework examples/kafka`.
 1. This will create a clone of the `hello-world` service which you should then modify.
2. Next change directory to where the Kafka service definition is: `cd examples/kafka/src/main/dist/`
3. With an editor of your choice open `svc.yml`. Notice that some values, such as `<task-name>_COUNT` and `<task-name>_CPUS` are mustached.[0]
4. The name of a task can be anything but since Kafka has brokers, change the type of the task in the Kafka pod from `server` to `broker`.
  1. Also notice that `count` is specified as a property of the `kafka` pod. This indicates there will be `count` kafka pods, each of which will run a `broker`.
5. Next, add the following property to the `broker` task in order to open up the necessary port.
   ```
   ports:
      - name: broker-port
        port: 9092
   ```

6. Now, let's modify the command that will start each `broker`. This is also a property of the `broker` task:

   ```
   cmd: "env && exec $MESOS_SANDBOX/kafka_2.11-0.10.0.0/bin/kafka-server-start.sh $MESOS_SANDBOX/kafka_2.11-0.10.0.0/config/server.properties"
   ```
7. Adding this to each `broker` will fetch the Kafka binary in order to execute the above command:

   ```
   uris:
      - "https://downloads.mesosphere.com/kafka/assets/kafka_2.11-0.10.0.0.tgz"
   ```
8. The following property allows for custom configuration of each `broker`:

   ```
   configurations:
      - template: "{{CONFIG_TEMPLATE_PATH}}/server.properties.mustache"
        dest: "kafka_2.11-0.10.0.0/config/server.properties"
   ```
   1. The `server.properties.mustache` should live in the current directory (`src/main/dist`).

9. Save and exit the `svc.yml` file then download the `server.properties.mustache` file mentioned above:
   ```
   curl -O https://raw.githubusercontent.com/mesosphere/dcos-commons/29c38ea81948c8fe550a9d77974f78155c318815/frameworks/kafka/src/main/dist/server.properties.mustache > server.properties.mustache
   ```

10. Next, create the symlink that's required to get the unit tests to pass and confirm that it's created:
   ```
   cd ../resources && ln -s ../dist/server.properties.mustache && ls -l
   ```

11. Open `/dcos-commons/examples/kafka/src/test/java/com/mesosphere/sdk/kafka/scheduler/ServiceSpecTest.java` and add the following beneath the currently set environment variables and make sure to import the URL type:
   ```
   // place at top
   import java.net.URL;
   ```
   ```
   URL resource = ServiceSpecTest.class.getClassLoader().getResource("server.properties.mustache");
   environmentVariables.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
   ```

12. Save and exit the file. Next, open `/dcos-commons/examples/kafka/universe/marathon.json.mustache` and add the following as the first item of the `env` list:
   ```
   "CONFIG_TEMPLATE_PATH": "scheduler",
   ```

14. Run the following command from the root directory of the service:
   ```
	 cd /dcos-commons/examples/kafka && ./build.sh local
   ```

15. Now, install the service via `dcos package install kafka` and visit your dashboard to see Kafka running: http://172.17.0.2/#/services/%2Fkafka/

[0] The mustaching provides support for dynamically updating a service's configuration at runtime.
