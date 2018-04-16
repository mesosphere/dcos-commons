package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.PodSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import java.util.*;

/**
 * Offer evaluation tests concerning Linux Capabilities.
 */
public class LinuxCapabilitiesEvaluationStage implements OfferEvaluationStage  {

    private final Logger logger;
    private final String taskName;

    public LinuxCapabilitiesEvaluationStage(
            String taskName,
            Optional<String> resourceNamespace) {
        this.logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
        this.taskName = taskName;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        //all offers are valid since Offers do not send capabilities.
        //TODO: Once capabilities come in offers change logic here.
        PodSpec podSpec = podInfoBuilder.getPodInstance().getPod();
        Collection<Protos.CapabilityInfo.Capability> requestedCapabilities = podSpec.getCapabilities();

        if (!requestedCapabilities.isEmpty()) {
            for (Protos.CapabilityInfo.Capability capability : podSpec.getCapabilities()) {
                podInfoBuilder.getTaskBuilder(taskName)
                        .getContainerBuilder()
                            .getLinuxInfoBuilder().setEffectiveCapabilities(podInfoBuilder
                                .getTaskBuilder(taskName)
                                    .getContainerBuilder()
                                       .getLinuxInfoBuilder()
                                            .getEffectiveCapabilitiesBuilder()
                                                .addCapabilities(capability));
                }
        }

        return EvaluationOutcome.pass(
                this,
                "Providing requested capabilities",
                podSpec.getCapabilities(),
                taskName).build();

    }
}

