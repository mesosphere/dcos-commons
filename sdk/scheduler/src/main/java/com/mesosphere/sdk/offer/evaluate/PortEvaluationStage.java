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

    private final String portName;
    private final int begin;
    private final int end;
    private final Optional<String> customEnvKey;

    private String resourceId;

    public PortEvaluationStage(
            Protos.Resource resource,
            String taskName,
            String portName,
            int begin,
            int end,
            Optional<String> customEnvKey) {
        super(resource, taskName);
        this.portName = portName;
        this.begin = begin;
        this.end = end;
        this.customEnvKey = customEnvKey;
    }

    public PortEvaluationStage(
            Protos.Resource resource,
            String portName,
            int begin,
            int end,
            Optional<String> customEnvKey) {
        this(resource, null, portName, begin, end, customEnvKey);
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        int assignedBegin = begin;
        int assignedEnd = end;

        if (begin == 0) {
            // If begin == 0, this is a dynamic port. Dynamic port ranges are not supported, so we assume a single port.
            // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
            Protos.CommandInfo commandInfo = getTaskName().isPresent() ?
                    podInfoBuilder.getTaskBuilder(getTaskName().get()).getCommand() :
                    podInfoBuilder.getExecutorBuilder().get().getCommand();
            String taskPort = CommandUtils.getEnvVar(commandInfo, getSinglePortEnvName());
            if (taskPort != null) {
                // Re-use previously assigned value (don't change port value once assigned).
                assignedBegin = Integer.parseInt(taskPort);
            } else {
                // No previous value found, assign new value.
                Optional<Integer> dynamicPort = selectDynamicPort(mesosResourcePool, podInfoBuilder);
                if (!dynamicPort.isPresent()) {
                    return fail(this,
                            "No ports were available for dynamic claim in offer: %s",
                            mesosResourcePool.getOffer().toString());
                }
                assignedBegin = dynamicPort.get();
            }
            // No port range support here per above, so end == begin:
            assignedEnd = assignedBegin;
        } else {
            assignedBegin = begin;
            assignedEnd = end;
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
        super.setResourceRequirement(getPortRequirement(getResourceRequirement(), assignedBegin, assignedEnd));

        return super.evaluate(mesosResourcePool, podInfoBuilder);
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        final Protos.Value.Range portRange = resource.getRanges().getRange(0);
        Protos.Resource.Builder resourceBuilder;

        if (getTaskName().isPresent()) {
            String taskName = getTaskName().get();
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
            taskBuilder.setCommand(withPortEnvAdded(taskBuilder.getCommand(), portRange));

            // Add port to the health check (if defined)
            if (taskBuilder.hasHealthCheck()) {
                taskBuilder.getHealthCheckBuilder().setCommand(
                        withPortEnvAdded(taskBuilder.getHealthCheckBuilder().getCommand(), portRange));
            } else {
                LOGGER.info("Health check is not defined for task: {}", taskName);
            }

            // Add port to the readiness check (if defined)
            try {
                Optional<Protos.HealthCheck> readinessCheck = CommonTaskUtils.getReadinessCheck(taskBuilder.build());
                if (readinessCheck.isPresent()) {
                    Protos.HealthCheck readinessCheckToMutate = readinessCheck.get();
                    Protos.HealthCheck readinessCheckWithPort = Protos.HealthCheck.newBuilder(readinessCheckToMutate)
                            .setCommand(withPortEnvAdded(readinessCheckToMutate.getCommand(), portRange))
                            .build();
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
            executorBuilder.setCommand(withPortEnvAdded(executorBuilder.getCommand(), portRange));
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
     * Returns the expected environment variable name in the case of a single port. Port ranges have a suffix added to
     * this name.
     */
    private String getSinglePortEnvName() {
        String draftEnvName = customEnvKey.isPresent()
                ? customEnvKey.get() // use custom name as-is
                : Constants.PORT_NAME_TASKENV_PREFIX + portName; // PORT_[name]
        // Envvar should be uppercased with invalid characters replaced with underscores:
        return TaskUtils.toEnvName(draftEnvName);
    }

    /**
     * Returns a copy of the provided {@link Protos.CommandInfo} which has been populated with one or more suitable
     * environment variables which advertise a single port or range of ports to a containerized process.
     */
    private Protos.CommandInfo withPortEnvAdded(Protos.CommandInfo commandInfo, Protos.Value.Range portRange) {
        Protos.CommandInfo.Builder commandInfoBuilder = commandInfo.toBuilder();
        Protos.Environment.Builder envBuilder = commandInfoBuilder.getEnvironmentBuilder();
        String envName = getSinglePortEnvName();
        if (portRange.getBegin() == portRange.getEnd()) {
            // single port: PORT_[name]
            envBuilder.addVariablesBuilder()
                    .setName(envName)
                    .setValue(Long.toString(portRange.getBegin()));
        } else {
            // port range: PORT_[name]_BEGIN and PORT_[name]_END
            envBuilder.addVariablesBuilder()
                    .setName(String.format("%s%s", envName, Constants.PORT_NAME_TASKENV_SUFFIX_RANGE_BEGIN))
                    .setValue(Long.toString(portRange.getBegin()));
            envBuilder.addVariablesBuilder()
                    .setName(String.format("%s%s", envName, Constants.PORT_NAME_TASKENV_SUFFIX_RANGE_END))
                    .setValue(Long.toString(portRange.getEnd()));
        }
        return commandInfoBuilder.build();
    }

    private static ResourceRequirement getPortRequirement(ResourceRequirement resourceRequirement, int begin, int end) {
        Protos.Resource.Builder builder = resourceRequirement.getResource().toBuilder();
        builder.clearRanges().getRangesBuilder().addRange(Protos.Value.Range.newBuilder().setBegin(begin).setEnd(end));

        return new ResourceRequirement(builder.build());
    }
}
