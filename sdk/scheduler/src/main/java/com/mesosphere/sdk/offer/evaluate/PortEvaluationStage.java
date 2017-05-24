package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.stream.IntStream;

/**
 * This class evaluates an offer for a single port against an {@link OfferRequirement}, finding a port dynamically
 * in the offer where specified by the framework and modifying {@link org.apache.mesos.Protos.TaskInfo} and
 * {@link org.apache.mesos.Protos.ExecutorInfo} where appropriate so that the port is available in their respective
 * environments.
 */
public class PortEvaluationStage extends ResourceEvaluationStage implements OfferEvaluationStage {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortEvaluationStage.class);

    protected final PortSpec portSpec;

    public PortEvaluationStage(
            PortSpec portSpec,
            String taskName,
            Optional<String> resourceId) {
        super(portSpec, resourceId, taskName);
        this.portSpec = portSpec;
    }

    protected long getPort() {
        return portSpec.getValue().getRanges().getRange(0).getBegin();
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
        Protos.CommandInfo commandInfo = getTaskName().isPresent() ?
                podInfoBuilder.getTaskBuilder(getTaskName().get()).getCommand() :
                podInfoBuilder.getExecutorBuilder().get().getCommand();
        Optional<String> taskPort = CommandUtils.getEnvVar(commandInfo, getPortEnvironmentVariable());

        long assignedPort = getPort();
        if (assignedPort == 0 && taskPort.isPresent()) {
            assignedPort = Integer.parseInt(taskPort.get());
        } else if (assignedPort == 0) {
            Optional<Integer> dynamicPort = selectDynamicPort(mesosResourcePool, podInfoBuilder);
            if (!dynamicPort.isPresent()) {
                return EvaluationOutcome.fail(this,
                        "No ports were available for dynamic claim in offer: %s",
                        mesosResourcePool.getOffer().toString());
            }

            assignedPort = dynamicPort.get();
        }

        ResourceSpec resourceSpec = DefaultResourceSpec.newBuilder(this.resourceSpec)
                .value(Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.RANGES)
                        .setRanges(Protos.Value.Ranges.newBuilder()
                                .addRange(Protos.Value.Range.newBuilder()
                                        .setBegin(assignedPort)
                                        .setEnd(assignedPort)))
                        .build())
                .build();
        this.resourceSpec = resourceSpec;

        EvaluationOutcome evaluationOutcome = super.evaluate(mesosResourcePool, podInfoBuilder);
        if (!evaluationOutcome.isPassing()) {
            return evaluationOutcome;
        }
        return EvaluationOutcome.pass(this, evaluationOutcome.getOfferRecommendations(), "Found port");
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

    private static Optional<Integer> selectDynamicPort(
            MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        /*
        // Also check dynamically allocated ports.
        for (Protos.Resource.Builder resourceBuilder : podInfoBuilder.getResourceBuilders()) {
            if (resourceBuilder.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
                resourceBuilder.getRanges().getRangeList().stream()
                        .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
                        .filter(p -> p != 0)
                        .forEach(consumedPorts::add);
            }
        }
        */

        Protos.Value availablePorts = mesosResourcePool.getUnreservedMergedPool().get(Constants.PORTS_RESOURCE_TYPE);
        Optional<Integer> dynamicPort = Optional.empty();
        if (availablePorts != null) {
            dynamicPort = availablePorts.getRanges().getRangeList().stream()
                    .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
                    .findFirst();
        }

        return dynamicPort;
    }

    private void setPortEnvironmentVariable(Protos.CommandInfo.Builder commandInfoBuilder, long port) {
        CommandUtils.setEnvVar(commandInfoBuilder, getPortEnvironmentVariable(), Long.toString(port));
    }

    /**
     * Returns a environment variable-style rendering of the provided {@code envKey}.
     * Invalid characters are replaced with underscores.
     */
    private String getPortEnvironmentVariable() {
        String draftEnvName = portSpec.getEnvKey().isPresent()
                ? portSpec.getEnvKey().get() // use custom name as-is
                : EnvConstants.PORT_NAME_TASKENV_PREFIX + portSpec.getPortName(); // PORT_[name]
        // Envvar should be uppercased with invalid characters replaced with underscores:
        return TaskUtils.toEnvName(draftEnvName);
    }
}
