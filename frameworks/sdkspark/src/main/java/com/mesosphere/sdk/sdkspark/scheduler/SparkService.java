package com.mesosphere.sdk.sdkspark.scheduler;

import com.mesosphere.sdk.scheduler.AnalyticsScheduler;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;

/**
 * Spark Scheduler
 */
class SparkService extends DefaultService {

    public SparkService(File pathToServiceSpec, SchedulerFlags schedulerFlags) throws Exception {
        super(createSchedulerBuilder(pathToServiceSpec, schedulerFlags));
    }

    public SparkService(RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) throws Exception {
        super(createSchedulerBuilder(rawServiceSpec, schedulerFlags));
    }

    public static DefaultScheduler.Builder createSchedulerBuilder(
            RawServiceSpec rawServiceSpec,
            SchedulerFlags schedulerFlags) throws Exception {
        DefaultServiceSpec yamlServiceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerFlags).build();

        PodSpec yamlCoordinatorPod = yamlServiceSpec.getPods().stream()
                .filter(podSpec -> podSpec.getType().equals("coordinator"))
                .findAny().orElseThrow(() -> new IllegalArgumentException("Missing Coordinator pod"));

        DefaultPodSpec.Builder coordinatorPodBuilder = DefaultPodSpec.newBuilder(schedulerFlags.getExecutorURI())
                .type(yamlCoordinatorPod.getType())
                .user(yamlCoordinatorPod.getUser().get())
                .addTask(makeDriverTask(yamlCoordinatorPod))
                .count(yamlCoordinatorPod.getCount())
                .addUris(yamlCoordinatorPod.getUris())
                .image(yamlCoordinatorPod.getImage().get())
                .networks(yamlCoordinatorPod.getNetworks())
                .rlimits(yamlCoordinatorPod.getRLimits())
                .volumes(yamlCoordinatorPod.getVolumes())
                .secrets(yamlCoordinatorPod.getSecrets())
                .preReservedRole(yamlCoordinatorPod.getPreReservedRole())
                .sharePidNamespace(yamlCoordinatorPod.getSharePidNamespace());

        if (yamlCoordinatorPod.getPlacementRule().isPresent()) {
            coordinatorPodBuilder.placementRule(yamlCoordinatorPod.getPlacementRule().get());
        }

        DefaultServiceSpec.Builder finalizedServiceSpecBuilder = DefaultServiceSpec.newBuilder()
                .name(yamlServiceSpec.getName())
                .role(yamlServiceSpec.getRole())
                .principal(yamlServiceSpec.getPrincipal())
                .zookeeperConnection(yamlServiceSpec.getZookeeperConnection())
                .webUrl(yamlServiceSpec.getWebUrl())
                .addPod(coordinatorPodBuilder.build())
                .addPod(yamlServiceSpec
                        .getPods().stream()
                        .filter(podSpec -> podSpec.getType().equals("executor"))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Cannot find executor pod")))
                .user(yamlServiceSpec.getUser());

        if (yamlServiceSpec.getReplacementFailurePolicy().isPresent()) {
            finalizedServiceSpecBuilder.replacementFailurePolicy(yamlServiceSpec.getReplacementFailurePolicy().get());
        }

        return AnalyticsScheduler.newBuilder(finalizedServiceSpecBuilder.build(), schedulerFlags)
                .setPlansFrom(rawServiceSpec);
    }

    private static DefaultScheduler.Builder createSchedulerBuilder(
            File pathToServiceSpec,
            SchedulerFlags schedulerFlags) throws Exception {
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(pathToServiceSpec).build();
        return createSchedulerBuilder(rawServiceSpec, schedulerFlags);
    }

    private static TaskSpec getDriverTask(PodSpec coordinatorPod) throws Exception {
        return coordinatorPod.getTasks().stream()
                .filter(taskSpec -> taskSpec.getName().equals("driver"))
                .findAny().orElseThrow(() -> new IllegalArgumentException("Cannot find driver task"));
    }

    private static TaskSpec makeDriverTask(PodSpec yamlCoordinatorPod) throws Exception {
        TaskSpec yamlDriverTask = getDriverTask(yamlCoordinatorPod);
        CommandSpec yamlCommand = yamlDriverTask.getCommand()
                .orElseThrow(() -> new IllegalArgumentException("Cannot find command for driver task"));
        int maxCores = Math.round(Float.parseFloat(yamlCommand.getEnvironment().get("EXECUTOR_CORES")) *
                Integer.parseInt(yamlCommand.getEnvironment().get("EXECUTOR_COUNT")));
        String[] parts = yamlCommand.getEnvironment().get("SPARK_APP_URL").split("/");
        String application = parts[parts.length - 1];
        String sparkSubmitCommand = yamlCommand.getValue().trim() +
                String.format(" --conf spark.cores.max=%s ", maxCores) +
                String.format("$MESOS_SANDBOX/%s ", application) +
                String.format("%s%n", yamlCommand.getEnvironment().get("APPLICATION_ARGS"));
        DefaultCommandSpec commandBuilder = DefaultCommandSpec.newBuilder()
                .environment(yamlCommand.getEnvironment())
                .value(sparkSubmitCommand).build();
        return DefaultTaskSpec.newBuilder(yamlDriverTask)
                .commandSpec(commandBuilder)
                .build();
    }
}
