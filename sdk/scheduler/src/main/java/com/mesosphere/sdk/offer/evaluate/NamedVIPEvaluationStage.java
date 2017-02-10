package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.ResourceUtils;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;

/**
 * This class evaluates an offer against a given {@link OfferRequirement} for port resources as in
 * {@link PortEvaluationStage}, additionally setting {@link org.apache.mesos.Protos.DiscoveryInfo} properly for
 * DC/OS to pick up the specified named VIP mapping.
 */
public class NamedVIPEvaluationStage extends PortEvaluationStage {
    private final String protocol;
    private final DiscoveryInfo.Visibility visibility;
    private final String vipName;
    private final Integer vipPort;

    public NamedVIPEvaluationStage(
            Protos.Resource resource,
            String taskName,
            String envKey,
            Integer port,
            String protocol,
            DiscoveryInfo.Visibility visibility,
            String vipName,
            Integer vipPort) {
        super(resource, taskName, envKey, port);
        this.protocol = protocol;
        this.visibility = visibility;
        this.vipName = vipName;
        this.vipPort = vipPort;
    }

    public NamedVIPEvaluationStage(
            Protos.Resource resource,
            String portName,
            Integer port,
            String protocol,
            DiscoveryInfo.Visibility visibility,
            String vipName,
            Integer vipPort) {
        this(resource, null, portName, port, protocol, visibility, vipName, vipPort);
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        super.setProtos(podInfoBuilder, resource);

        // If this is an existing TaskInfo or ExecutorInfo with the VIP already set, we don't have to do anything.
        if (getTaskName().isPresent() && !isVIPSet(podInfoBuilder.getTaskBuilder(getTaskName().get()).getDiscovery())) {
            // Set the VIP on the TaskInfo.
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
            ResourceUtils.addVIP(taskBuilder, vipName, vipPort, protocol, visibility, resource);
        } else if (podInfoBuilder.getExecutorBuilder().isPresent() &&
                !isVIPSet(podInfoBuilder.getExecutorBuilder().get().getDiscovery())) {
            // Set the VIP on the ExecutorInfo.
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            ResourceUtils.addVIP(executorBuilder, vipName, vipPort, protocol, visibility, resource);
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
