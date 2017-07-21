package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * This class evaluates an offer for a single port against a
 * {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement}, finding a port dynamically in the offer where
 * specified by the framework and modifying {@link org.apache.mesos.Protos.TaskInfo} and
 * {@link org.apache.mesos.Protos.ExecutorInfo} where appropriate so that the port is available in their respective
 * environments.
 */
public class PortEvaluationStage implements OfferEvaluationStage {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortEvaluationStage.class);
    private final boolean useHostPorts;

    protected PortSpec portSpec;
    private final String taskName;
    private Optional<String> resourceId;

    public PortEvaluationStage(PortSpec portSpec, String taskName, Optional<String> resourceId) {
        this.portSpec = portSpec;
        this.taskName = taskName;
        this.resourceId = resourceId;
        this.useHostPorts = requireHostPorts(portSpec.getNetworkNames());
    }

    protected long getPort() {
        return portSpec.getValue().getRanges().getRange(0).getBegin();
    }

    protected long getResourcePort(Protos.Resource resource) {
        return resource.getRanges().getRange(0).getBegin();
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        long assignedPort = getPort();
        if (assignedPort == 0) {
            // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
            Optional<Long> priorTaskPort = podInfoBuilder.lookupPriorTaskPortValue(
                    getTaskName().get(), portSpec.getName(), getPortEnvironmentVariable(portSpec));
            if (priorTaskPort.isPresent()) {
                // Reuse the prior port value.
                assignedPort = priorTaskPort.get();
            } else {
                // Choose a new port value.
                Optional<Integer> dynamicPort = useHostPorts ?
                        selectDynamicPort(mesosResourcePool, podInfoBuilder) :
                        selectOverlayPort(podInfoBuilder);
                if (!dynamicPort.isPresent()) {
                    return EvaluationOutcome.fail(
                            this,
                            "No ports were available for dynamic claim in offer," +
                                    " and no %s envvar was present in prior %s: %s %s",
                            getPortEnvironmentVariable(portSpec),
                            getTaskName().isPresent() ? "task " + getTaskName().get() : "executor",
                            TextFormat.shortDebugString(mesosResourcePool.getOffer()),
                            podInfoBuilder.toString())
                            .build();
                }
                assignedPort = dynamicPort.get();
            }
        }

        // Update portSpec to reflect the assigned port value (for example, to reflect a dynamic port allocation):
        Protos.Value.Builder valueBuilder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES);
        valueBuilder.getRangesBuilder().addRangeBuilder()
                .setBegin(assignedPort)
                .setEnd(assignedPort);
        portSpec = PortSpec.withValue(portSpec, valueBuilder.build());

        if (useHostPorts) {
            OfferEvaluationUtils.ReserveEvaluationOutcome reserveEvaluationOutcome =
                    OfferEvaluationUtils.evaluateSimpleResource(this, portSpec, resourceId, mesosResourcePool);
            EvaluationOutcome evaluationOutcome = reserveEvaluationOutcome.getEvaluationOutcome();
            if (!evaluationOutcome.isPassing()) {
                return evaluationOutcome;
            }

            resourceId = reserveEvaluationOutcome.getResourceId();
            setProtos(podInfoBuilder, ResourceBuilder.fromSpec(portSpec, resourceId).build());
            return EvaluationOutcome.pass(
                    this,
                    evaluationOutcome.getOfferRecommendations(),
                    "Offer contains required %sport: '%s' with resourceId: '%s'",
                    resourceId.isPresent() ? "previously reserved " : "",
                    portSpec.getPortName(),
                    resourceId)
                    .mesosResource(evaluationOutcome.getMesosResource().get())
                    .build();
        } else {
            setProtos(podInfoBuilder, ResourceBuilder.fromSpec(portSpec, resourceId).build());
            return EvaluationOutcome.pass(
                    this,
                    "Port %s doesn't require resource reservation, ignoring resource requirements and using port %d",
                    portSpec.getPortName(), assignedPort)
                    .build();
        }
    }

    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        long port = resource.getRanges().getRange(0).getBegin();

        if (getTaskName().isPresent()) {
            String taskName = getTaskName().get();
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);

            if (!taskBuilder.hasDiscovery()) {
                // Initialize with defaults:
                taskBuilder.getDiscoveryBuilder()
                        .setVisibility(Constants.DEFAULT_TASK_DISCOVERY_VISIBILITY)
                        .setName(taskBuilder.getName());
            }
            taskBuilder.getDiscoveryBuilder().getPortsBuilder().addPortsBuilder()
                    .setNumber((int) port)
                    .setVisibility(portSpec.getVisibility())
                    .setProtocol(DcosConstants.DEFAULT_IP_PROTOCOL)
                    .setName(portSpec.getPortName());

            // Add port to the main task environment:
            taskBuilder.getCommandBuilder().setEnvironment(
                    withPortEnvironmentVariable(taskBuilder.getCommandBuilder().getEnvironment(), port));

            // Add port to the health check environment (if defined):
            if (taskBuilder.hasHealthCheck()) {
                taskBuilder.getHealthCheckBuilder().getCommandBuilder().setEnvironment(
                        withPortEnvironmentVariable(taskBuilder.getHealthCheck().getCommand().getEnvironment(), port));
            } else {
                LOGGER.info("Health check is not defined for task: {}", taskName);
            }

            // Add port to the readiness check environment (if a readiness check is defined):
            try {
                taskBuilder.setLabels(new TaskLabelWriter(taskBuilder)
                        .setReadinessCheckEnvvar(getPortEnvironmentVariable(portSpec), Long.toString(port))
                        .toProto());
            } catch (TaskException e) {
                LOGGER.error("Got exception while adding PORT env var to ReadinessCheck", e);
            }

            if (useHostPorts) { // we only use the resource if we're using the host ports
                taskBuilder.addResources(resource);
            }
        } else {
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            executorBuilder.getCommandBuilder().setEnvironment(
                    withPortEnvironmentVariable(executorBuilder.getCommandBuilder().getEnvironment(), port));
            if (useHostPorts) {
                executorBuilder.addResources(resource);
            }
        }
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

    private static Optional<Integer> selectOverlayPort(PodInfoBuilder podInfoBuilder) {
        // take the next available port in the range.
        Optional<Integer> dynamicPort = Optional.empty();
        for (Integer i = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START;
             i <= DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_END; i++) {
            if (!podInfoBuilder.isAssignedOverlayPort(i.longValue())) {
                dynamicPort = Optional.of(i);
                podInfoBuilder.addAssignedOverlayPort(i.longValue());
                break;
            }
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

    private Protos.Environment withPortEnvironmentVariable(Protos.Environment environment, long port) {
        return EnvUtils.withEnvVar(environment, getPortEnvironmentVariable(portSpec), Long.toString(port));
    }

    /**
     * Returns the appropriate environment variable to be used for the provided {@link PortSpec}.
     */
    static String getPortEnvironmentVariable(PortSpec portSpec) {
        String draftEnvName = portSpec.getEnvKey().isPresent()
                ? portSpec.getEnvKey().get() // use custom name as-is
                : EnvConstants.PORT_NAME_TASKENV_PREFIX + portSpec.getPortName(); // PORT_[name]
        // Envvar should be uppercased with invalid characters replaced with underscores:
        return EnvUtils.toEnvName(draftEnvName);
    }

    private static boolean requireHostPorts(Collection<String> networkNames) {
        if (networkNames.isEmpty()) {  // no network names, must be on host network and use the host IP
            return true;
        } else {
            return networkNames.stream()
                    .filter(DcosConstants::networkSupportsPortMapping)
                    .count() > 0;
        }
    }

    protected Optional<String> getTaskName() {
        return Optional.ofNullable(taskName);
    }
}
