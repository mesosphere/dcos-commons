# Kafka Labs

In this tutorial we'll be walking through step-by-step instructions on getting a Kafka service up and running. This tutorial assumes you've already setup a local cluster using the [Quick Start](https://github.com/mesosphere/dcos-commons/blob/pods/README.md) guide.

#### Steps:

1. Create a new service by running `./new-service examples/kafka` in the dcos-commons root directory. This will create a clone of the `hello-world` service which you can then modify.
2. `cd examples/kafka/src/main/dist/`
3. With an editor of your choice open `svc.yml`. Notice that some values, such as `<task-name>_COUNT` and `<task-name>_CPUS` are mustached.[0]
4. Since Kafka has Brokers, change the type of the task in the Kafka pod from `server` to `broker`.
  1. Also notice that `count` is specified as a property of the `kafka` pod. This indicates there will be `count` kafka pods, each of whom will run a `broker`.
5. Next we'll open up the necessary port. Add the following as a property to the `broker` task:
   ```
   ports:
      - name: broker-port
        port: 9092
   ```

6. Now let's add the command that will start each `broker`. This is also a property of the `broker` task:
   
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
   1. The `server.properties.mustache` should live in the current directory (`src/main/dist`). Let's download it.

9. `wget https://raw.githubusercontent.com/mesosphere/dcos-commons/29c38ea81948c8fe550a9d77974f78155c318815/frameworks/kafka/src/main/dist/server.properties.mustache`

10. `cd ../resources && ln -s ../dist/server.properties.mustache`
  1. We need to create this symlink in order to get the unit tests to pass.
  
11. Open `src/test/java/com/mesosphere/sdk/kafka/scheduler/ServiceSpecTest.java` and add the following beneath the currently set environment variables and make sure to import the URL type:
   ```
   // place at top
   import java.net.URL;
   ```
   ```
   URL resource = ServiceSpecTest.class.getClassLoader().getResource("server.properties.mustache");
   environmentVariables.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
   ```


12. Open `universe/marathon.json.mustache` and add the following to the `env` list:
   ```
   "CONFIG_TEMPLATE_PATH": "scheduler"
   ```
   1. Note: if you add it to the end of the list, make sure to place a comma (,) at the end of the second last item.
   
13. Let's build and publish the service. From the root directory of the service: `./build.sh local`

14. Once done, install the service via: `dcos package install kafka`

Now visit your dashboard to see Kafka running: http://172.17.0.2/#/services/%2Fkafka/

[0] The mustaching provides support for dynamically updating a service's configuration at runtime.
