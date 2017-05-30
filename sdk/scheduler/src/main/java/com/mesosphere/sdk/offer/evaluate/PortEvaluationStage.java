package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
    private final int port;
    private final Optional<String> customEnvKey;
    private final boolean useHostPorts;

    private String resourceId;

    public PortEvaluationStage(
            Protos.Resource resource,
            String taskName,
            String portName,
            int port,
            Optional<String> customEnvKey,
            boolean useHostPorts){
        super(resource, taskName);
        this.portName = portName;
        this.port = port;
        this.customEnvKey = customEnvKey;
        this.useHostPorts = useHostPorts;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        Protos.CommandInfo commandInfo = getTaskName().isPresent() ?
                podInfoBuilder.getTaskBuilder(getTaskName().get()).getCommand() :
                podInfoBuilder.getExecutorBuilder().get().getCommand();
        Optional<String> taskPort = EnvUtils.getEnvVar(commandInfo.getEnvironment(), getPortEnvironmentVariable());
        int assignedPort = port;

        // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
        if (assignedPort == 0) {
            if (taskPort.isPresent()) {
                assignedPort = Integer.parseInt(taskPort.get());
            } else {
                Optional<Integer> dynamicPort = useHostPorts ?
                        selectDynamicPort(mesosResourcePool, podInfoBuilder)
                        : selectOverlayPort(podInfoBuilder);
                if (!dynamicPort.isPresent()) {
                    return fail(this,
                            "No ports were available for dynamic claim in offer: %s",
                            mesosResourcePool.getOffer().toString());
                }
                LOGGER.info("assigned port dynamically {}", dynamicPort.get().toString());
                assignedPort = dynamicPort.get();
            }
        }

        // If this is not the first port evaluation stage in this evaluation run, and this is a new pod being launched,
        // we want to use the reservation ID we created for the first port in this cycle for all subsequent ports.
        resourceId = getResourceId(getTaskName().isPresent()
                        ? podInfoBuilder.getTaskBuilder(getTaskName().get()).getResourcesList()
                        : podInfoBuilder.getExecutorBuilder().get().getResourcesList(),
                Constants.PORTS_RESOURCE_TYPE).orElse("");

        super.setResourceRequirement(getPortRequirement(getResourceRequirement(), assignedPort));

        if (useHostPorts) {
            return super.evaluate(mesosResourcePool, podInfoBuilder);
        } else {
            ResourceRequirement resourceRequirement= getResourceRequirement();
            setProtos(podInfoBuilder, resourceRequirement.getResource());
            return pass(
                    this,
                    Collections.emptyList(),
                    "Not using host ports, ignoring port resource requirements, using port %s",
                    TextFormat.shortDebugString(resourceRequirement.getValue()));
        }
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        long port = resource.getRanges().getRange(0).getBegin();

        if (getTaskName().isPresent()) {
            String taskName = getTaskName().get();
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
            taskBuilder.getCommandBuilder().setEnvironment(
                    withPortEnvironmentVariable(taskBuilder.getCommandBuilder().getEnvironment(), port));

            // Add port to the health check (if defined)
            if (taskBuilder.hasHealthCheck()) {
                taskBuilder.getHealthCheckBuilder().getCommandBuilder().setEnvironment(
                        withPortEnvironmentVariable(taskBuilder.getHealthCheck().getCommand().getEnvironment(), port));
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
            if (useHostPorts) { // we only use the resource if we're using the host ports
                Collection<Resource> updatedResourceList =
                        updateOrAddRangedResource(taskBuilder.getResourcesList(), resource);
                taskBuilder
                        .clearResources()
                        .addAllResources(updatedResourceList);
            }

        } else {
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            executorBuilder.getCommandBuilder().setEnvironment(
                    withPortEnvironmentVariable(executorBuilder.getCommandBuilder().getEnvironment(), port));
            if (useHostPorts) {
                Collection<Resource> updatedResourceList =
                        updateOrAddRangedResource(executorBuilder.getResourcesList(), resource);
                executorBuilder
                        .clearResources()
                        .addAllResources(updatedResourceList);
            }
        }
    }

    private static Optional<String> getResourceId(List<Resource> resources, String resourceName) {
        return resources.stream()
                .filter(resource -> resource.getName().equals(resourceName))
                .map(ResourceCollectionUtils::getResourceId)
                .filter(resourceId -> resourceId.isPresent())
                .map(resourceId -> resourceId.get())
                .findFirst();
    }

    private static List<Resource> updateOrAddRangedResource(List<Resource> existingResources, Resource resource) {
        List<Resource> updatedResources = new ArrayList<>();
        boolean updateOccurred = false;
        for (Resource existingResource : existingResources) {
            if (existingResource.getName().equals(resource.getName())) {
                // Merge onto matching resource
                updateOccurred = true;
                Value.Builder valueBuilder = Value.newBuilder().setType(Value.Type.RANGES);
                valueBuilder.getRangesBuilder().addAllRange(
                        RangeAlgorithms.mergeRanges(
                                existingResource.getRanges().getRangeList(), resource.getRanges().getRangeList()));
                updatedResources.add(ResourceBuilder.fromExistingResource(existingResource)
                        .setValue(valueBuilder.build())
                        .build());
            } else {
                updatedResources.add(existingResource);
            }
        }
        if (!updateOccurred) {
            // Match not found, add new resource to end
            updatedResources.add(resource);
        }
        return updatedResources;
    }

    @Override
    protected Protos.Resource toFulfilledResource(Protos.Resource resource) {
        Protos.Resource reservedResource = super.toFulfilledResource(resource);
        if (!StringUtils.isBlank(resourceId)) {
            reservedResource = ResourceBuilder.fromExistingResource(reservedResource)
                    .setResourceId(resourceId)
                    .build();
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

    private static Optional<Integer> selectOverlayPort(PodInfoBuilder podInfoBuilder) {
        // take the next available port in the range.
        Optional<Integer> dynamicPort = Optional.empty();
        for (int i = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START;
             i <= DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_END; i++) {
            if (!podInfoBuilder.isAssignedOverlayPort(i)) {
                dynamicPort = Optional.of(i);
                podInfoBuilder.addAssignedOverlayPort(i);
                break;
            }
        }
        return dynamicPort;
    }

    private Protos.Environment withPortEnvironmentVariable(Protos.Environment environment, long port) {
        return EnvUtils.withEnvVar(environment, getPortEnvironmentVariable(), Long.toString(port));
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
        return EnvUtils.toEnvName(draftEnvName);
    }

    private static ResourceRequirement getPortRequirement(ResourceRequirement resourceRequirement, int port) {
        Protos.Resource.Builder builder = resourceRequirement.getResource().toBuilder();
        builder.clearRanges().getRangesBuilder().addRange(Protos.Value.Range.newBuilder().setBegin(port).setEnd(port));

        return new ResourceRequirement(builder.build());
    }
}
