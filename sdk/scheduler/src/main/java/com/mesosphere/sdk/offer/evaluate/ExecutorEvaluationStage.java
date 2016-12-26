package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.executor.ExecutorUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.offer.OfferRequirement;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * This class evaluates an offer against a given {@link OfferRequirement}, ensuring that executor IDs match between
 * the two and setting the executor ID for a newly-launching pod.
 */
public class ExecutorEvaluationStage implements OfferEvaluationStage {
    private Optional<Protos.ExecutorID> executorId;

    public ExecutorEvaluationStage(Protos.ExecutorID executorId) {
        this.executorId = Optional.ofNullable(executorId);
    }

    public ExecutorEvaluationStage() {
        this(null);
    }

    @Override
    public void evaluate(
            MesosResourcePool offerResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) throws OfferEvaluationException {
        Protos.Offer offer = offerResourcePool.getOffer();
        if (offerRequirement.getExecutorRequirementOptional().isPresent()) {
            Protos.ExecutorInfo executorInfo = offerRequirement.getExecutorRequirementOptional()
                    .get().getExecutorInfo();
            if (!hasExpectedExecutorId(offerResourcePool.getOffer())) {
                throw new OfferEvaluationException(String.format(
                        "Offer: '%s' does not contain the needed ExecutorID: '%s'",
                        offer.getId().getValue().toString(), executorInfo.getExecutorId().getValue().toString()));
            }

            // Set executor ID *after* the other check above for its presence:
            Protos.ExecutorID newExecutorId;
            if (executorId.isPresent()) {
                newExecutorId = executorId.get();
            } else {
                newExecutorId = ExecutorUtils.toExecutorId(executorInfo.getName());
            }
            offerRequirement.updateExecutorRequirement(executorInfo.toBuilder()
                    .setExecutorId(newExecutorId)
                    .build());
        }
    }

    private boolean hasExpectedExecutorId(Protos.Offer offer) {
        if (!executorId.isPresent()) {
            return true;
        }

        for (Protos.ExecutorID execId : offer.getExecutorIdsList()) {
            if (execId.equals(executorId.get())) {
                return true;
            }
        }

        return false;
    }
}
