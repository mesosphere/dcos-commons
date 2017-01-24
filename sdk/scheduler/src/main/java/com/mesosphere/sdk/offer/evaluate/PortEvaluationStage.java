package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.*;

/**
 * This class evaluates an offer for its port resources against an {@link OfferRequirement}, finding ports dynamically
 * in the offer where specified by the framework and modifying {@link org.apache.mesos.Protos.TaskInfo} and
 * {@link org.apache.mesos.Protos.ExecutorInfo} where appropriate so that the ports are available in their respective
 * environments.
 */
public class PortEvaluationStage extends ResourceEvaluationStage implements OfferEvaluationStage {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortEvaluationStage.class);
    private final String portName;
    private final int port;

    public PortEvaluationStage(Protos.Resource resource, String taskName, String portName, int port) {
        super(resource, taskName);
        this.portName = portName;
        this.port = port;
    }

    public PortEvaluationStage(Protos.Resource resource, String portName, int port) {
        this(resource, null, portName, port);
    }

    @Override
    public EvaluationOutcome evaluate(
            MesosResourcePool mesosResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) {
        // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
        Protos.CommandInfo commandInfo = getTaskName().isPresent() ?
                offerRequirement.getTaskRequirement(getTaskName().get()).getTaskInfo().getCommand() :
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getCommand();
        String taskPort = CommandUtils.getEnvVar(commandInfo, getPortEnvironmentVariable(portName));
        int assignedPort = port;

        if (assignedPort == 0 && taskPort != null) {
            assignedPort = Integer.parseInt(taskPort);
        } else if (assignedPort == 0) {
            Optional<Integer> dynamicPort = selectDynamicPort(mesosResourcePool, offerRequirement);
            if (!dynamicPort.isPresent()) {
                return fail(this,
                        "No ports were available for dynamic claim in offer: %s",
                        mesosResourcePool.getOffer().toString());
            }

            assignedPort = dynamicPort.get();
        }

        super.setResourceRequirement(getPortRequirement(getResourceRequirement(), assignedPort));
        return super.evaluate(mesosResourcePool, offerRequirement, offerRecommendationSlate);
    }

    @Override
    protected void setProtos(OfferRequirement offerRequirement, Protos.Resource resource) {
        long port = resource.getRanges().getRange(0).getBegin();

        if (getTaskName().isPresent()) {
            String taskName = getTaskName().get();
            Protos.TaskInfo taskInfo = offerRequirement.getTaskRequirement(taskName).getTaskInfo();
            Protos.Resource ports = ResourceUtils.getResource(taskInfo, Constants.PORTS_RESOURCE_TYPE);

            if (ports.hasReservation() && !ports.getReservation().getLabels().getLabels(0).getValue().equals("")) {
                // If we've already created a reservation for this task's ports in a previous evaluation stage,
                // then we want to use that reservation ID instead of the one created for the fulfilled resource in this
                // stage.
                ports = ResourceUtils.mergeRanges(ports, resource);
            } else {
                ports = ResourceUtils.mergeRanges(resource, ports);
            }

            Protos.TaskInfo.Builder taskInfoBuilder = taskInfo.toBuilder();
            try {
                ResourceUtils.setResource(taskInfoBuilder, ports);
            } catch (TaskException e) {
                LOGGER.error("Failed to set resource on TaskInfo.", e);
            }

            taskInfoBuilder.setCommand(
                    CommandUtils.addEnvVar(
                            taskInfoBuilder.getCommand(), getPortEnvironmentVariable(portName), Long.toString(port)));

            // Add port to the health check (if defined)
            if (taskInfoBuilder.hasHealthCheck()) {
                taskInfoBuilder.getHealthCheckBuilder().setCommand(
                        CommandUtils.addEnvVar(
                                taskInfoBuilder.getHealthCheckBuilder().getCommand(),
                                getPortEnvironmentVariable(portName),
                                Long.toString(port)));
            } else {
                LOGGER.info("Health check is not defined for task: {}", taskName);
            }

            // Add port to the readiness check (if defined)
            try {
                Optional<Protos.HealthCheck> readinessCheck = CommonTaskUtils.getReadinessCheck(taskInfo);
                if (readinessCheck.isPresent()) {
                    Protos.HealthCheck readinessCheckToMutate = readinessCheck.get();
                    Protos.CommandInfo readinessCommandWithPort = CommandUtils.addEnvVar(
                            readinessCheckToMutate.getCommand(),
                            getPortEnvironmentVariable(portName),
                            Long.toString(port));
                    Protos.HealthCheck readinessCheckWithPort = Protos.HealthCheck.newBuilder(readinessCheckToMutate)
                            .setCommand(readinessCommandWithPort).build();
                    CommonTaskUtils.setReadinessCheck(taskInfoBuilder, readinessCheckWithPort);
                } else {
                    LOGGER.info("Readiness check is not defined for task: {}", taskName);
                }
            } catch (TaskException e) {
                LOGGER.error("Got exception while adding PORT env vars to ReadinessCheck", e);
            }
            offerRequirement.updateTaskRequirement(taskName, taskInfoBuilder.build());
        } else {
            Protos.ExecutorInfo executorInfo = offerRequirement.getExecutorRequirementOptional()
                    .get()
                    .getExecutorInfo();
            Protos.Resource.Builder ports = ResourceUtils.getResource(
                    executorInfo, Constants.PORTS_RESOURCE_TYPE).toBuilder();

            ports.getRangesBuilder().addRangeBuilder().setBegin(port).setEnd(port);

            Protos.ExecutorInfo.Builder executorInfoBuilder = executorInfo.toBuilder();
            ResourceUtils.setResource(executorInfoBuilder, ports.build());
            executorInfoBuilder.setCommand(
                    CommandUtils.addEnvVar(
                            executorInfoBuilder.getCommand(),
                            getPortEnvironmentVariable(portName),
                            Long.toString(port)));
            offerRequirement.updateExecutorRequirement(executorInfoBuilder.build());
        }
    }

    private static Optional<Integer> selectDynamicPort(
            MesosResourcePool mesosResourcePool, OfferRequirement offerRequirement) {
        // We don't want to dynamically consume a port that's explicitly claimed by this pod accidentally, so
        // compile a list of those to check against the offered ports.
        Set<Integer> consumedPorts = new HashSet<>();
        for (Protos.Resource resource : offerRequirement.getResources()) {
            if (resource.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
                resource.getRanges().getRangeList().stream()
                        .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
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
     * Returns a environment variable-style rendering of the provided {@code portName}. The name is uppercased, "PORT_"
     * is added to the beginning, and invalid characters are replaced with underscores.
     */
    private static String getPortEnvironmentVariable(String portName) {
        return "PORT_" + portName.toUpperCase().replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static ResourceRequirement getPortRequirement(ResourceRequirement resourceRequirement, int port) {
        Protos.Resource.Builder builder = resourceRequirement.getResource().toBuilder();
        builder.clearRanges().getRangesBuilder().addRange(Protos.Value.Range.newBuilder().setBegin(port).setEnd(port));

        return new ResourceRequirement(builder.build());
    }
}
