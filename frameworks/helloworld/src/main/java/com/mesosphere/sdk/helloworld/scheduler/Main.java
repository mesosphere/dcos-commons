package com.mesosphere.sdk.helloworld.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.specification.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        new DefaultService(new File(args[0]));
    }

    private static final String SLEEP_DURATION_KEY = "SLEEP_DURATION";
    private static final String HELLO_CPUS_KEY = "HELLO_CPUS";
    private static final String ROLE = "hello-world-role";

    public ServiceSpec getServiceSpec() {
        return DefaultServiceSpec.newBuilder()
                .name("hello-world")
                .principal("hello-world-principal")
                .zookeeperConnection(DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING)
                .apiPort(Integer.valueOf(System.getenv("PORT0")))
                .pods(getPodSpecs())
                .build();
    }

    public List<PodSpec> getPodSpecs() {
        PodSpec helloPod = DefaultPodSpec.newBuilder()
                .count(Integer.valueOf(System.getenv("HELLO_COUNT")))
                .tasks(getHelloTask())
                .build();

        PodSpec worldPod = DefaultPodSpec.newBuilder()
                .count(Integer.valueOf(System.getenv("WORLD_COUNT")))
                .tasks(getWorldTask())
                .build();
    }


    public List<TaskSpec> getHelloTask() {
        Map<String, String> env = new HashMap<String, String>();
        env.put(SLEEP_DURATION_KEY, System.getenv(SLEEP_DURATION_KEY));

        List<ResourceSpecification> resources = Arrays.asList(
                DefaultResourceSpecification.newBuilder()
                        .name("cpus")
                        .value(
                                Protos.Value.newBuilder()
                                        .setScalar(Protos.Value.Scalar.newBuilder()
                                                .setValue(Double.valueOf(System.getenv(HELLO_CPUS_KEY)))
                                                .build())
                                        .build())
                        .role(ROLE)
                        .build());



        )

        ResourceSet helloResourceSet = DefaultResourceSet.newBuilder()
                .id("hello-resources")
                .cpus(1)
                .memory()
                .ad
                .addPort()
                .addPort()

                .build();

        TaskSpec helloTask = DefaultTaskSpec.newBuilder()
                .goalState(TaskSpec.GoalState.RUNNING)
                .commandSpec(DefaultCommandSpec.newBuilder()
                        .value("echo hello >> hello-container-path/output && sleep $SLEEP_DURATION")
                        .environment(env)
                        .build())
                .resourceSet()


    }
}
