package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.*;

/**
 * This class evaluates an offer for a single port against an {@link OfferRequirement}, finding a port dynamically
 * in the offer where specified by the framework and modifying {@link org.apache.mesos.Protos.TaskInfo} and
 * {@link org.apache.mesos.Protos.ExecutorInfo} where appropriate so that the port is available in their respective
 * environments.
 */
public class PortEvaluationStage extends ResourceEvaluationStage implements OfferEvaluationStage {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortEvaluationStage.class);

    private final String envKey;
    private final int port;
    private String resourceId;

    public PortEvaluationStage(Protos.Resource resource, String taskName, String envKey, int port) {
        super(resource, taskName);
        this.envKey = envKey;
        this.port = port;
    }

    public PortEvaluationStage(Protos.Resource resource, String portName, int port) {
        this(resource, null, portName, port);
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
        Protos.CommandInfo commandInfo = getTaskName().isPresent() ?
                podInfoBuilder.getTaskBuilder(getTaskName().get()).getCommand() :
                podInfoBuilder.getExecutorBuilder().get().getCommand();
        String taskPort = CommandUtils.getEnvVar(commandInfo, getPortEnvironmentVariable(envKey));
        int assignedPort = port;

        if (assignedPort == 0 && taskPort != null) {
            assignedPort = Integer.parseInt(taskPort);
        } else if (assignedPort == 0) {
            Optional<Integer> dynamicPort = selectDynamicPort(mesosResourcePool, podInfoBuilder);
            if (!dynamicPort.isPresent()) {
                return fail(this,
                        "No ports were available for dynamic claim in offer: %s",
                        mesosResourcePool.getOffer().toString());
            }

            assignedPort = dynamicPort.get();
        }

        // If this is not the first port evaluation stage in this evaluation run, and this is a new pod being launched,
        // we want to use the reservation ID we created for the first port in this cycle for all subsequent ports.
        try {
            resourceId = getTaskName().isPresent() ?
                    ResourceUtils.getResourceId(ResourceUtils.getResource(
                            podInfoBuilder.getTaskBuilder(getTaskName().get()), Constants.PORTS_RESOURCE_TYPE)) :
                    ResourceUtils.getResourceId(ResourceUtils.getResource(
                            podInfoBuilder.getExecutorBuilder().get(), Constants.PORTS_RESOURCE_TYPE));
        } catch (IllegalArgumentException e) {
            // There have been no previous ports in this evaluation cycle, so there's no resource on the task builder
            // to get the resource id from.
            resourceId = "";
        }
        super.setResourceRequirement(getPortRequirement(getResourceRequirement(), assignedPort));

        return super.evaluate(mesosResourcePool, podInfoBuilder);
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        long port = resource.getRanges().getRange(0).getBegin();
        Protos.Resource.Builder resourceBuilder;

        if (getTaskName().isPresent()) {
            String taskName = getTaskName().get();
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);

            taskBuilder.setCommand(
                    CommandUtils.addEnvVar(
                            taskBuilder.getCommand(), getPortEnvironmentVariable(envKey), Long.toString(port)));

            // Add port to the health check (if defined)
            if (taskBuilder.hasHealthCheck()) {
                taskBuilder.getHealthCheckBuilder().setCommand(
                        CommandUtils.addEnvVar(
                                taskBuilder.getHealthCheckBuilder().getCommand(),
                                getPortEnvironmentVariable(envKey),
                                Long.toString(port)));
            } else {
                LOGGER.info("Health check is not defined for task: {}", taskName);
            }

            // Add port to the readiness check (if defined)
            try {
                Optional<Protos.HealthCheck> readinessCheck = CommonTaskUtils.getReadinessCheck(taskBuilder.build());
                if (readinessCheck.isPresent()) {
                    Protos.HealthCheck readinessCheckToMutate = readinessCheck.get();
                    Protos.CommandInfo readinessCommandWithPort = CommandUtils.addEnvVar(
                            readinessCheckToMutate.getCommand(),
                            getPortEnvironmentVariable(envKey),
                            Long.toString(port));
                    Protos.HealthCheck readinessCheckWithPort = Protos.HealthCheck.newBuilder(readinessCheckToMutate)
                            .setCommand(readinessCommandWithPort).build();
                    CommonTaskUtils.setReadinessCheck(taskBuilder, readinessCheckWithPort);
                } else {
                    LOGGER.info("Readiness check is not defined for task: {}", taskName);
                }
            } catch (TaskException e) {
                LOGGER.error("Got exception while adding PORT env vars to ReadinessCheck", e);
            }
            resourceBuilder = ResourceUtils.getResourceBuilder(taskBuilder, resource);
        } else {
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            executorBuilder.setCommand(
                    CommandUtils.addEnvVar(
                            executorBuilder.getCommand(),
                            getPortEnvironmentVariable(envKey),
                            Long.toString(port)));

            resourceBuilder = ResourceUtils.getResourceBuilder(executorBuilder, resource);
        }

        ResourceUtils.mergeRanges(resourceBuilder, resource);
    }

    @Override
    protected Protos.Resource getFulfilledResource(Protos.Resource resource) {
        Protos.Resource reservedResource = super.getFulfilledResource(resource);
        if (!StringUtils.isBlank(resourceId)) {
            reservedResource = ResourceUtils.clearResourceId(reservedResource);
            reservedResource = ResourceUtils.setResourceId(reservedResource, resourceId);
        }

        return reservedResource;
    }

    private static Optional<Integer> selectDynamicPort(
            MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        // We don't want to dynamically consume a port that's explicitly claimed by this pod accidentally, so
        // compile a list of those to check against the offered ports.
        OfferRequirement offerRequirement = podInfoBuilder.getOfferRequirement();
        Set<Integer> consumedPorts = new HashSet<>();
        for (Protos.Resource resource : offerRequirement.getResources()) {
            if (resource.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
                resource.getRanges().getRangeList().stream()
                        .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
                        .filter(p -> p != 0)
                        .forEach(consumedPorts::add);
            }
        }

        // Also check dynamically allocated ports.
        for (Protos.Resource.Builder resourceBuilder : podInfoBuilder.getResourceBuilders()) {
            if (resourceBuilder.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
                resourceBuilder.getRanges().getRangeList().stream()
                        .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
                        .filter(p -> p != 0)
                        .forEach(consumedPorts::add);
            }
        }

        Protos.Value availablePorts = mesosResourcePool.getUnreservedMergedPool().get(Constants.PORTS_RESOURCE_TYPE);
        Optional<Integer> dynamicPort = Optional.empty();
        if (availablePorts != null) {
            dynamicPort = availablePorts.getRanges().getRangeList().stream()
                    .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
                    .filter(p -> !consumedPorts.contains(p))
                    .findFirst();
        }

        return dynamicPort;
    }

    /**
     * Returns a environment variable-style rendering of the provided {@code envKey}.
     * Invalid characters are replaced with underscores.
     */
    private static String getPortEnvironmentVariable(String envKey) {
        return String.format(TaskUtils.toEnvName(envKey));
    }

    private static ResourceRequirement getPortRequirement(ResourceRequirement resourceRequirement, int port) {
        Protos.Resource.Builder builder = resourceRequirement.getResource().toBuilder();
        builder.clearRanges().getRangesBuilder().addRange(Protos.Value.Range.newBuilder().setBegin(port).setEnd(port));

        return new ResourceRequirement(builder.build());
    }
}
