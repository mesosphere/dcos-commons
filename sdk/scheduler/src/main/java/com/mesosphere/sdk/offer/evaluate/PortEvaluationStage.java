package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class evaluates an offer for a single port against a
 * {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement}, finding a port dynamically in the offer where
 * specified by the framework and modifying {@link org.apache.mesos.Protos.TaskInfo} and
 * {@link org.apache.mesos.Protos.ExecutorInfo} where appropriate so that the port is available in their respective
 * environments.
 */
public class PortEvaluationStage extends ResourceEvaluationStage implements OfferEvaluationStage {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortEvaluationStage.class);

    protected final PortSpec portSpec;

    public PortEvaluationStage(PortSpec portSpec, String taskName, Optional<String> resourceId) {
        super(portSpec, resourceId, taskName);
        this.portSpec = portSpec;
    }

    protected long getPort() {
        return portSpec.getValue().getRanges().getRange(0).getBegin();
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
        Optional<String> taskPort = getTaskName().isPresent()
                ? podInfoBuilder.getLastTaskEnv(getTaskName().get(), getPortEnvironmentVariable())
                : podInfoBuilder.getLastExecutorEnv(getPortEnvironmentVariable());

        long assignedPort = getPort();
        if (assignedPort == 0 && taskPort.isPresent()) {
            assignedPort = Integer.parseInt(taskPort.get());
        } else if (assignedPort == 0) {
            Optional<Integer> dynamicPort = selectDynamicPort(mesosResourcePool, podInfoBuilder);
            if (!dynamicPort.isPresent()) {
                return EvaluationOutcome.fail(this,
                        "No ports were available for dynamic claim in offer, and no %s envvar was present in prior %s: %s %s",
                        getPortEnvironmentVariable(),
                        getTaskName().isPresent() ? "task " + getTaskName().get() : "executor",
                        TextFormat.shortDebugString(mesosResourcePool.getOffer()),
                        podInfoBuilder.toString());
            }

            assignedPort = dynamicPort.get();
        }

        Protos.Value.Builder valueBuilder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES);
        valueBuilder.getRangesBuilder().addRangeBuilder()
                .setBegin(assignedPort)
                .setEnd(assignedPort);
        this.resourceSpec = DefaultResourceSpec.newBuilder(this.resourceSpec)
                .value(valueBuilder.build())
                .build();

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
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
            setPortEnvironmentVariable(taskBuilder.getCommandBuilder(), port);

            // Add port to the health check (if defined)
            if (taskBuilder.hasHealthCheck()) {
                setPortEnvironmentVariable(taskBuilder.getHealthCheckBuilder().getCommandBuilder(), port);
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
        Set<Integer> consumedPorts = new HashSet<>();

        // We don't want to accidentally dynamically consume a port that's explicitly claimed elsewhere in this pod, so
        // compile a list of those to check against the offered ports.
        for (TaskSpec task : podInfoBuilder.getPodInstance().getPod().getTasks()) {
            for (ResourceSpec resourceSpec : task.getResourceSet().getResources()) {
                if (resourceSpec instanceof PortSpec) {
                    PortSpec portSpec = (PortSpec) resourceSpec;
                    if (portSpec.getPort() != 0) {
                        consumedPorts.add((int) portSpec.getPort());
                    }
                }
            }
        }

        // Also check other dynamically allocated ports which had been taken by earlier stages of this evaluation round.
        for (Protos.Resource.Builder resourceBuilder : podInfoBuilder.getTaskResourceBuilders()) {
            consumedPorts.addAll(getPortsInResource(resourceBuilder.build()));
        }
        for (Protos.Resource.Builder resourceBuilder : podInfoBuilder.getExecutorResourceBuilders()) {
            consumedPorts.addAll(getPortsInResource(resourceBuilder.build()));
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

    private static Set<Integer> getPortsInResource(Protos.Resource resource) {
        if (!resource.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
            return Collections.emptySet();
        }
        return resource.getRanges().getRangeList().stream()
                .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
                .filter(p -> p != 0)
                .collect(Collectors.toSet());
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
