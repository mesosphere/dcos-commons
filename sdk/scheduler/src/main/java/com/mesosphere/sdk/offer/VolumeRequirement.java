package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.VolumeEvaluationStage;
import org.apache.mesos.Protos;

/**
 * A VolumeRequirement encapsulates the configuration required for volume creation.
 */
public class VolumeRequirement extends ResourceRequirement {
    public VolumeRequirement(Protos.Resource resource) {
        super(resource);
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new VolumeEvaluationStage(getResource(), taskName);
    }
}
