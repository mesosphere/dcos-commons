package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import org.apache.mesos.Protos;

import java.util.Optional;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class evaluates an ensuring that the offered Executor ID matches the needed ID
 * and setting the executor ID for a newly-launching pod.
 */
public class ExecutorEvaluationStage implements OfferEvaluationStage {
    private final Optional<Protos.ExecutorID> executorId;

    /**
     * Instantiate with an expected {@link org.apache.mesos.Protos.ExecutorID} to check for in offers. If not found,
     * the offer will be rejected by this stage.
     * @param executorId the executor ID to look for in incoming offers
     */
    public ExecutorEvaluationStage(Optional<Protos.ExecutorID> executorId) {
        this.executorId = executorId;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        if (!podInfoBuilder.getExecutorBuilder().isPresent()) {
            return pass(this, "No executor requirement defined");
        }

        Protos.Offer offer = mesosResourcePool.getOffer();
        Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
        if (!hasExpectedExecutorId(offer)) {
            return fail(this,
                    "Offer does not contain the needed Executor ID: '%s'",
                    executorBuilder.getExecutorId().getValue());
        }

        // Set executor ID *after* the other check above for its presence:
        Protos.ExecutorID newExecutorId;
        if (executorId.isPresent()) {
            newExecutorId = executorId.get();
        } else {
            newExecutorId = CommonIdUtils.toExecutorId(executorBuilder.getName());
        }
        executorBuilder.setExecutorId(newExecutorId);
        return pass(this, "Offer contains the matching Executor ID");
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
