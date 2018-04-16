package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.PodSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import java.util.*;

public class LinuxCapabilitiesEvaluationStage implements OfferEvaluationStage  {

    private final Logger logger;
    private final Optional<String> resourceId;
    private final Optional<String> resourceNamespace;
    private final String taskName;
    private final PodSpec podSpec;

    public CapabilitiesEvaluationStage(
            PodSpec podSpec,
            String taskName,
            Optional<String> resourceId,
            Optional<String> resourceNamespace) {
        this.logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
        this.resourceId = resourceId;
        this.resourceNamespace = resourceNamespace;
        this.taskName = taskName;
        this.podSpec = podSpec;

    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {


        //all offers are valid since Offers do not send capabilities.
        //TODO: Once capabilities come in offers change logic here.

        Collection<Protos.CapabilityInfo.Capability> requestedCapabilities = getCapabilityInfo(this.podSpec.getCapabilities());

        if (!requestedCapabilities.isEmpty()) {
            for (Protos.CapabilityInfo.Capability capability : getCapabilityInfo(podSpec.getCapabilities()))
                podInfoBuilder.getTaskBuilder(taskName).getContainerBuilder().getLinuxInfoBuilder().setEffectiveCapabilities(podInfoBuilder
                        .getTaskBuilder(taskName)
                        .getContainerBuilder()
                        .getLinuxInfoBuilder()
                        .getEffectiveCapabilitiesBuilder()
                        .addCapabilities(capability)
                );
        }

        return EvaluationOutcome.pass(
                this,
                "Providing requested capabilities",
                podSpec.getCapabilities(),
                taskName).build();

    }

    private static Collection<Protos.CapabilityInfo.Capability> getCapabilityInfo(Collection<String> capabilities) {
        //In the case that ALL is passed give all linux capabilities
        //otherwise pass the set provided in the podSpec
        Collection<Protos.CapabilityInfo.Capability> capabilitySet = new ArrayList<>();
        if (capabilities.size() == 1 && capabilities.toArray()[0] == "ALL") {
            for(Protos.CapabilityInfo.Capability capability : Protos.CapabilityInfo.Capability.values()) {
                capabilitySet.add(capability);
            }
        } else {
            for(String capability : capabilities) {
                capabilitySet.add(Protos.CapabilityInfo.Capability.valueOf(capability));
            }
        }

        return capabilitySet;
    }
}

