package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.executor.ExecutorUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.offer.OfferRequirement;
import org.apache.mesos.Protos;

/**
 * This class evaluates an offer against a given {@link OfferRequirement}, ensuring that executor IDs match between
 * the two and setting the executor ID for a newly-launching pod.
 */
public class ExecutorEvaluationStage implements OfferEvaluationStage {
    private final Protos.ExecutorID executorId;

    /**
     * Instantiate with an expected {@link org.apache.mesos.Protos.ExecutorID} to check for in offers. If not found,
     * the offer will be rejected by this stage.
     * @param executorId the executor ID to look for in incoming offers
     */
    public ExecutorEvaluationStage(Protos.ExecutorID executorId) {
        this.executorId = executorId;
    }

    /**
     * Instantiate with no expected {@link org.apache.mesos.Protos.ExecutorID} to check for in offers. A new ID will
     * be created for the {@link org.apache.mesos.Protos.ExecutorInfo} at evaluation time.
     */
    public ExecutorEvaluationStage() {
        this(null);
    }

    @Override
    public void evaluate(
            MesosResourcePool mesosResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) throws OfferEvaluationException {
        if (!offerRequirement.getExecutorRequirementOptional().isPresent()) {
            return;
        }

        Protos.Offer offer = mesosResourcePool.getOffer();
        Protos.ExecutorInfo executorInfo = offerRequirement.getExecutorRequirementOptional()
                .get().getExecutorInfo();
        if (!hasExpectedExecutorId(offer)) {
            throw new OfferEvaluationException(String.format(
                    "Offer: '%s' does not contain the needed ExecutorID: '%s'",
                    offer.getId().getValue().toString(), executorInfo.getExecutorId().getValue().toString()));
        }

        // Set executor ID *after* the other check above for its presence:
        Protos.ExecutorID newExecutorId;
        if (executorId != null) {
            newExecutorId = executorId;
        } else {
            newExecutorId = ExecutorUtils.toExecutorId(executorInfo.getName());
        }
        offerRequirement.updateExecutorRequirement(executorInfo.toBuilder()
                .setExecutorId(newExecutorId)
                .build());
    }

    private boolean hasExpectedExecutorId(Protos.Offer offer) {
        if (executorId == null) {
            return true;
        }

        for (Protos.ExecutorID execId : offer.getExecutorIdsList()) {
            if (execId.equals(executorId)) {
                return true;
            }
        }

        return false;
    }
}
