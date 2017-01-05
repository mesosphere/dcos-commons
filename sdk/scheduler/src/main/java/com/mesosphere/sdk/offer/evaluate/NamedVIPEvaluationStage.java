package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.ResourceUtils;
import org.apache.mesos.Protos;

/**
 * This class evaluates an offer against a given {@link OfferRequirement} for port resources as in
 * {@link PortEvaluationStage}, additionally setting {@link org.apache.mesos.Protos.DiscoveryInfo} properly for
 * DC/OS to pick up the specified named VIP mapping.
 */
public class NamedVIPEvaluationStage extends PortEvaluationStage implements OfferEvaluationStage {
    private final String vipName;
    private final Integer vipPort;

    public NamedVIPEvaluationStage(
            Protos.Resource resource, String taskName, String portName, Integer port, String vipName, Integer vipPort) {
        super(resource, taskName, portName, port);
        this.vipName = vipName;
        this.vipPort = vipPort;
    }

    public NamedVIPEvaluationStage(
            Protos.Resource resource, String portName, Integer port, String vipName, Integer vipPort) {
        this(resource, null, portName, port, vipName, vipPort);
    }

    @Override
    protected void setProtos(OfferRequirement offerRequirement, Protos.Resource resource) {
        super.setProtos(offerRequirement, resource);

        // If this is an existing TaskInfo or ExecutorInfo with the VIP already set, we don't have to do anything.
        if (getTaskName().isPresent() &&
                !isVIPSet(offerRequirement.getTaskRequirement(getTaskName().get()).getTaskInfo().getDiscovery())) {
            // Set the VIP on the TaskInfo.
            Protos.TaskInfo.Builder taskInfoBuilder = offerRequirement
                    .getTaskRequirement(getTaskName().get()).getTaskInfo().toBuilder();

            ResourceUtils.addVIP(taskInfoBuilder, vipName, vipPort, resource);
            offerRequirement.updateTaskRequirement(getTaskName().get(), taskInfoBuilder.build());
        } else if (offerRequirement.getExecutorRequirementOptional().isPresent() &&
                !isVIPSet(offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getDiscovery())) {
            // Set the VIP on the ExecutorInfo.
            Protos.ExecutorInfo.Builder executorInfoBuilder = offerRequirement.getExecutorRequirementOptional()
                    .get()
                    .getExecutorInfo()
                    .toBuilder();

            ResourceUtils.addVIP(executorInfoBuilder, vipName, vipPort, resource);
            offerRequirement.updateExecutorRequirement(executorInfoBuilder.build());
        }
    }

    private boolean isVIPSet(Protos.DiscoveryInfo discoveryInfo) {
        for (Protos.Label l : discoveryInfo.getLabels().getLabelsList()) {
            if (l.getKey().startsWith(ResourceUtils.VIP_PREFIX) &&
                    l.getValue().equals(String.format("%s:%d", vipName, vipPort))) {
                return true;
            }
        }

        return false;
    }
}
