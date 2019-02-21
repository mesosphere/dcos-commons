package com.mesosphere.sdk.portworx.scheduler;

import com.mesosphere.sdk.config.validate.TaskEnvCannotChange;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.portworx.api.*;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.util.*;

import org.apache.mesos.Protos;

/**
 * Portworx service.
 */
public class Main {
    private static final String PORTWORX_POD_NAME = "portworx";
    private static final String INSTALL_TASK_NAME = "install";
    private static final String ENV_PORTWORX_START_PORT = "PORTWORX_START_PORT";
    private static final long DEFAULT_START_PORT = 9001;
    private static final long PORT_COUNT = 19;
    private static final long DEFAULT_RANGE_EXTRA_PORTS = 3;
    private static final long DEFAULT_SDK_REST_PORT_OFFSET = 20;
    private static final long CUSTOM_SDK_REST_PORT_OFFSET = 17;
    private static final String SDK_REST_PORT_NAME = "sdk";
    private static final Integer MIN_REPLACE_DELAY_MIN = 0;


    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one file argument, got: " + Arrays.toString(args));
        }
        SchedulerRunner
                .fromSchedulerBuilder(createSchedulerBuilder(new File(args[0])))
                .run();
    }

    private static SchedulerBuilder createSchedulerBuilder(File yamlSpecFile) throws Exception {
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlSpecFile).build();
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(
                rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile()).build();
        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
            .replacementFailurePolicy(getReplacementFailurePolicy())
            .build();

        SchedulerBuilder schedulerBuilder =
                DefaultScheduler.newBuilder(setPortResources(serviceSpec), schedulerConfig)
                .setCustomConfigValidators(Arrays.asList(
                        new TaskEnvCannotChange("portworx", "install", ENV_PORTWORX_START_PORT),
                        new TaskEnvCannotChange("etcd-cluster", "node", "ETCD_ENABLED"),
                        new TaskEnvCannotChange("etcd-proxy", "node", "ETCD_ENABLED"),
                        new TaskEnvCannotChange("lighthouse", "start", "LIGHTHOUSE_ENABLED"),
                        new TaskEnvCannotChange("lighthouse", "start", "LIGHTHOUSE_ADMIN_USER")))
                .setPlansFrom(rawServiceSpec);

        schedulerBuilder.setCustomResources(getResources(schedulerBuilder.getServiceSpec()));
        return schedulerBuilder;
    }

    private static ReplacementFailurePolicy getReplacementFailurePolicy() throws Exception {
        // Setting MinReplaceDelay to 0 min, to enable launching multiple failed tasks (node lost)
        // to start immediately instead of waiting for sometime to start another task.
        return ReplacementFailurePolicy.newBuilder()
            .permanentFailureTimoutMs(
                Integer.valueOf(System.getenv("TASK_FAILURE_TIMEOUT_MINUTES")))
            .minReplaceDelayMs(MIN_REPLACE_DELAY_MIN)
            .build();
    }

    private static ServiceSpec setPortResources(DefaultServiceSpec serviceSpec) throws Exception {
        Optional<PodSpec> podMatch = serviceSpec.getPods().stream()
                .filter(podSpec -> podSpec.getType().equals(PORTWORX_POD_NAME))
                .findFirst();
        if (!podMatch.isPresent()) {
            throw new IllegalStateException(String.format(
                    "Missing required pod named '%s' in service spec", PORTWORX_POD_NAME));
        }
        PodSpec portworxPod = podMatch.get();

        Optional<TaskSpec> taskMatch = portworxPod.getTasks().stream()
                .filter(taskSpec -> taskSpec.getName().equals(INSTALL_TASK_NAME))
                .findFirst();
        if (!taskMatch.isPresent()) {
            throw new IllegalStateException(String.format(
                    "Missing required task named '%s' in service spec", INSTALL_TASK_NAME));
        }
        TaskSpec installTask = taskMatch.get();

        DefaultResourceSet.Builder resourceSetBuilder =
                DefaultResourceSet.newBuilder((DefaultResourceSet) installTask.getResourceSet());
        String role;
        String preReservedRole = portworxPod.getPreReservedRole();
        if (preReservedRole != null && !preReservedRole.isEmpty() && !preReservedRole.equals("*")) {
            role = portworxPod.getPreReservedRole() + "/" + serviceSpec.getRole();
        } else {
            role = serviceSpec.getRole();
        }

        Long sdkRestPort = getSdkRestPort();
        for (Long portNumber : getPortList()) {
            if (portNumber.equals(sdkRestPort)) {
                continue;
            }
            resourceSetBuilder.addResource(new PortSpec(
                    Protos.Value.newBuilder()
                            .setRanges(Protos.Value.Ranges.newBuilder()
                                    .addRange(Protos.Value.Range.newBuilder()
                                            .setBegin(portNumber)
                                            .setEnd(portNumber)))
                            .setType(Protos.Value.Type.RANGES)
                            .build(),
                    role, preReservedRole, serviceSpec.getPrincipal(), null,
                    "px_" + String.valueOf(portNumber),
                    Protos.DiscoveryInfo.Visibility.CLUSTER, Collections.emptyList()));
        }

        resourceSetBuilder.addResource(new NamedVIPSpec(
                Protos.Value.newBuilder()
                        .setRanges(Protos.Value.Ranges.newBuilder()
                                .addRange(Protos.Value.Range.newBuilder()
                                        .setBegin(sdkRestPort)
                                        .setEnd(sdkRestPort)))
                        .setType(Protos.Value.Type.RANGES)
                        .build(),
                role, preReservedRole, serviceSpec.getPrincipal(), null,
                SDK_REST_PORT_NAME, DcosConstants.DEFAULT_IP_PROTOCOL, Protos.DiscoveryInfo.Visibility.CLUSTER,
                SDK_REST_PORT_NAME, sdkRestPort.intValue(), Collections.emptyList()));

        TaskSpec updatedInstallTask = DefaultTaskSpec.newBuilder(installTask)
                .resourceSet(resourceSetBuilder.build())
                .build();

        DefaultPodSpec.Builder updatedPortworxPod = DefaultPodSpec.newBuilder(portworxPod)
                .tasks(new ArrayList<>());
        for (TaskSpec spec : portworxPod.getTasks()) {
            if (spec.getName().equals(INSTALL_TASK_NAME)) {
                updatedPortworxPod.addTask(updatedInstallTask);
            } else {
                updatedPortworxPod.addTask(spec);
            }
        }

        DefaultServiceSpec.Builder updatedServiceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(new ArrayList<>());
        for (PodSpec spec : serviceSpec.getPods()) {
            if (spec.getType().equals(PORTWORX_POD_NAME)) {
                updatedServiceSpec.addPod(updatedPortworxPod.build());
            } else {
                updatedServiceSpec.addPod(spec);
            }
        }

        return updatedServiceSpec.build();
    }

    private static List<Long> getPortList() {
        Long startPort, portCount;
        try {
            startPort = Long.valueOf(System.getenv(ENV_PORTWORX_START_PORT));
        } catch (NumberFormatException e) {
            startPort = DEFAULT_START_PORT;
        }

        portCount = (startPort == DEFAULT_START_PORT) ?
                PORT_COUNT + DEFAULT_RANGE_EXTRA_PORTS : PORT_COUNT;

        List<Long> ports = new ArrayList<>();
        // Start port which is the REST server port is already reserved as VIP port.
        // So start port reservation from next port.
        for (long port = startPort + 1; port < startPort + portCount; port++) {
            ports.add(port);
        }
        return ports;
    }

    private static Long getSdkRestPort() {
        Long startPort = Long.valueOf(System.getenv(ENV_PORTWORX_START_PORT));
        if (startPort == DEFAULT_START_PORT) {
            return startPort + DEFAULT_SDK_REST_PORT_OFFSET;
        } else {
            return startPort + CUSTOM_SDK_REST_PORT_OFFSET;
        }
    }

    private static Collection<Object> getResources(ServiceSpec serviceSpec) {
        final Collection<Object> apiResources = new ArrayList<>();
        apiResources.add(new PortworxResource(serviceSpec));

        return apiResources;
    }
}
