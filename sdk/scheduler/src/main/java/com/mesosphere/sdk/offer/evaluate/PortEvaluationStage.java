package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * This class evaluates an offer for its port resources against an {@link OfferRequirement}, finding ports dynamically
 * in the offer where specified by the framework and modifying {@link org.apache.mesos.Protos.TaskInfo} and
 * {@link org.apache.mesos.Protos.ExecutorInfo} where appropriate so that the ports are available in their respective
 * environments.
 */
public class PortEvaluationStage extends ResourceEvaluationStage implements OfferEvaluationStage {
    private String portName;
    private Integer port;

    public PortEvaluationStage(Protos.Resource resource, String taskName, String portName, Integer port) {
        super(resource, taskName);
        this.portName = portName;
        this.port = port;
    }

    public PortEvaluationStage(Protos.Resource resource, String portName, Integer port) {
        this(resource, null, portName, port);
    }

    @Override
    public void evaluate(
            MesosResourcePool offerResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) throws OfferEvaluationException {
        // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
        Protos.CommandInfo commandInfo = getTaskName().isPresent() ?
                offerRequirement.getTaskRequirement(getTaskName().get()).getTaskInfo().getCommand() :
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getCommand();
        String taskPort = CommandUtils.getEnvVar(commandInfo, portName);
        Integer assignedPort = port;
        if (assignedPort == 0 && taskPort != null) {
            assignedPort = Integer.parseInt(taskPort);
        }

        if (assignedPort == 0) {
            // We don't want to dynamically consume a port that's explicitly claimed by this pod accidentally, so
            // compile a list of those to check against the offered ports.
            Set<Integer> consumedPorts = new HashSet<>();
            for (Protos.Resource resource : offerRequirement.getResources()) {
                if (resource.getName().equals("ports")) {
                    resource.getRanges().getRangeList().stream()
                            .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
                            .forEach(consumedPorts::add);
                }
            }

            Protos.Value availablePorts = offerResourcePool.getUnreservedMergedPool().get("ports");
            Optional<Integer> dynamicPort = Optional.empty();
            if (availablePorts != null) {
                dynamicPort = availablePorts.getRanges().getRangeList().stream()
                        .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
                        .filter(p -> !consumedPorts.contains(p))
                        .findFirst();
            }

            if (!dynamicPort.isPresent()) {
                throw new OfferEvaluationException(String.format(
                        "No ports were available for dynamic claim in offer: %s",
                        offerResourcePool.getOffer().toString()));
            }
            assignedPort = dynamicPort.get();
        }

        super.setResourceRequirement(getDynamicPortRequirement(getResourceRequirement(), assignedPort));
        super.evaluate(offerResourcePool, offerRequirement, offerRecommendationSlate);
    }

    @Override
    protected void setProtos(OfferRequirement offerRequirement, Protos.Resource resource) {
        long port = resource.getRanges().getRange(0).getBegin();

        if (getTaskName().isPresent()) {
            String taskName = getTaskName().get();
            Protos.TaskInfo taskInfo = offerRequirement.getTaskRequirement(taskName).getTaskInfo();
            Protos.TaskInfo.Builder taskInfoBuilder = taskInfo.toBuilder();
            Protos.Resource.Builder ports = ResourceUtils.getResource(taskInfo, "ports").toBuilder();

            ports.getRangesBuilder().addRangeBuilder().setBegin(port).setEnd(port);
            ResourceUtils.setResource(taskInfoBuilder, ports.build());

            taskInfoBuilder.setCommand(
                    CommandUtils.addEnvVar(
                            taskInfoBuilder.getCommand(), getPortEnvironmentVariable(portName), Long.toString(port)));
            offerRequirement.updateTaskRequirement(taskName, taskInfoBuilder.build());
        } else {
            Protos.ExecutorInfo executorInfo = offerRequirement.getExecutorRequirementOptional()
                    .get()
                    .getExecutorInfo();
            Protos.ExecutorInfo.Builder executorInfoBuilder = executorInfo.toBuilder();
            Protos.Resource.Builder ports = ResourceUtils.getResource(executorInfo, "ports").toBuilder();

            ports.getRangesBuilder().addRangeBuilder().setBegin(port).setEnd(port);
            ResourceUtils.setResource(executorInfoBuilder, ports.build());
            executorInfoBuilder.setCommand(
                    CommandUtils.addEnvVar(
                            executorInfoBuilder.getCommand(),
                            getPortEnvironmentVariable(portName),
                            Long.toString(port)));
            offerRequirement.updateExecutorRequirement(executorInfoBuilder.build());
        }
    }

    private static String getPortEnvironmentVariable(String portName) {
        return "PORT_" + portName;
    }

    private static ResourceRequirement getDynamicPortRequirement(
            ResourceRequirement resourceRequirement, Integer port) {
        Protos.Resource.Builder builder = resourceRequirement.getResource().toBuilder();
        builder.clearRanges().getRangesBuilder().addRange(Protos.Value.Range.newBuilder().setBegin(port).setEnd(port));

        return new ResourceRequirement(builder.build());
    }
}
