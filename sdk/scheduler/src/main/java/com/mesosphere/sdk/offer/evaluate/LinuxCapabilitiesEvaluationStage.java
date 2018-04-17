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

        logger.info("Evaluating Linux capabilities against requested capabilities");

        PodSpec podSpec = podInfoBuilder.getPodInstance().getPod();
        podInfoBuilder.getTaskBuilder(taskName)
                .getContainerBuilder()
                    .getLinuxInfoBuilder()
                        .setEffectiveCapabilities(getEffectiveCapabilities(podSpec));

        return EvaluationOutcome.pass(
                this,
                "Providing requested capabilities",
                podSpec.getCapabilities(),
                taskName).build();

    }

    private static Protos.CapabilityInfo getEffectiveCapabilities(PodSpec podSpec) {
        Protos.CapabilityInfo.Builder capabilityInfo = Protos.CapabilityInfo.newBuilder();

        for (Protos.CapabilityInfo.Capability capability : podSpec.getCapabilities()) {
            capabilityInfo.addCapabilities(capability);
        }

        return capabilityInfo.build();
    }
}

