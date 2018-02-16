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

    private final Optional<Protos.ExecutorID> id;

    /**
     * Instantiate with an expected {@link org.apache.mesos.Protos.ExecutorID} to check for in offers. If not found,
     * the offer will be rejected by this stage.
     * @param id the executor ID to look for in incoming offers
     */
    public ExecutorEvaluationStage(Optional<Protos.ExecutorID> id) {
        this.id = id;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        if (!podInfoBuilder.getExecutorBuilder().isPresent()) {
            return pass(this, "No executor requirement defined").build();
        }

        String idStr = id.isPresent() ? id.get().getValue() : "";

        if (!hasExpectedExecutorId(mesosResourcePool.getOffer())) {
            return fail(this,
                    "Offer does not contain the needed Executor ID: '%s'", idStr)
                    .build();
        }

        Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
        if (id.isPresent()) {
            executorBuilder.setExecutorId(id.get());
            return pass(
                    this,
                    "Offer contains the matching Executor ID: '%s'", idStr)
                    .build();
        } else {
            Protos.ExecutorID executorID = CommonIdUtils.toExecutorId(executorBuilder.getName());
            executorBuilder.setExecutorId(executorID);
            return pass(
                    this,
                    "No Executor ID required, generated: '%s'",
                    executorID.getValue()).build();
        }
    }

    private boolean hasExpectedExecutorId(Protos.Offer offer) {
        if (!id.isPresent()) {
            return true;
        }

        for (Protos.ExecutorID execId : offer.getExecutorIdsList()) {
            if (execId.equals(id.get())) {
                return true;
            }
        }

        return false;
    }
}
