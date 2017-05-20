package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;

import com.mesosphere.sdk.specification.PortSpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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
    private final Optional<String> customEnvKey;
    private final PortSpec portSpec;
    private String resourceId;

    protected final long port;

    public PortEvaluationStage(
            PortSpec portSpec,
            String taskName,
            String portName,
            long port,
            Optional<String> customEnvKey,
            Optional<String> resourceId) {
        super(portSpec, resourceId, taskName);
        this.portSpec = portSpec;
        this.portName = portName;
        this.port = port;
        this.customEnvKey = customEnvKey;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
        Protos.CommandInfo commandInfo = getTaskName().isPresent() ?
                podInfoBuilder.getTaskBuilder(getTaskName().get()).getCommand() :
                podInfoBuilder.getExecutorBuilder().get().getCommand();
        Optional<String> taskPort = CommandUtils.getEnvVar(commandInfo, getPortEnvironmentVariable());

        long assignedPort = port;
        if (assignedPort == 0 && taskPort.isPresent()) {
            assignedPort = Integer.parseInt(taskPort.get());
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

        return super.evaluate(mesosResourcePool, podInfoBuilder);
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        long port = resource.getRanges().getRange(0).getBegin();
        Protos.Resource.Builder resourceBuilder;

        if (getTaskName().isPresent()) {
            String taskName = getTaskName().get();
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
            setPortEnvironmentVariable(taskBuilder.getCommandBuilder(), port);

            // Add port to the health check (if defined)
            if (taskBuilder.hasHealthCheck()) {
                setPortEnvironmentVariable(taskBuilder.getHealthCheckBuilder().getCommandBuilder(), port);
            } else {
                LOGGER.info("Health check is not defined for task: {}", taskName);
            }

            // Add port to the readiness check (if a readiness check is defined)
            try {
                taskBuilder.setLabels(new SchedulerLabelWriter(taskBuilder)
                        .setReadinessCheckEnvvar(getPortEnvironmentVariable(), Long.toString(port))
                        .toProto());
            } catch (TaskException e) {
                LOGGER.error("Got exception while adding PORT env var to ReadinessCheck", e);
            }
            resourceBuilder = ResourceUtils.getResourceBuilder(taskBuilder, resource);
        } else {
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            setPortEnvironmentVariable(executorBuilder.getCommandBuilder(), port);
            resourceBuilder = ResourceUtils.getResourceBuilder(executorBuilder, resource);
        }

        ResourceUtils.mergeRanges(resourceBuilder, resource);
    }

    @Override
    protected Protos.Resource getFulfilledResource() {
        Protos.Resource reservedResource = super.getFulfilledResource();
        if (!StringUtils.isBlank(resourceId)) {
            reservedResource = ResourceUtils.setResourceId(ResourceUtils.clearResourceId(reservedResource), resourceId);
        }
        return reservedResource;
    }

    private static Optional<Integer> selectDynamicPort(
            MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        throw new UnsupportedOperationException("selectDynamicPort");
        /*
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
        */
    }

    private void setPortEnvironmentVariable(Protos.CommandInfo.Builder commandInfoBuilder, long port) {
        CommandUtils.setEnvVar(commandInfoBuilder, getPortEnvironmentVariable(), Long.toString(port));
    }

    /**
     * Returns a environment variable-style rendering of the provided {@code envKey}.
     * Invalid characters are replaced with underscores.
     */
    private String getPortEnvironmentVariable() {
        String draftEnvName = customEnvKey.isPresent()
                ? customEnvKey.get() // use custom name as-is
                : EnvConstants.PORT_NAME_TASKENV_PREFIX + portName; // PORT_[name]
        // Envvar should be uppercased with invalid characters replaced with underscores:
        return TaskUtils.toEnvName(draftEnvName);
    }
}
