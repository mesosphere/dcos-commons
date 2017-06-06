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
    private final Optional<Protos.ExecutorInfo> executorInfo;

    /**
     * Instantiate with an expected {@link org.apache.mesos.Protos.ExecutorID} to check for in offers. If not found,
     * the offer will be rejected by this stage.
     * @param executorInfo the executor ID to look for in incoming offers
     */
    public ExecutorEvaluationStage(Optional<Protos.ExecutorInfo> executorInfo) {
        this.executorInfo = executorInfo;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        if (!podInfoBuilder.getExecutorBuilder().isPresent()) {
            return pass(this, "No executor requirement defined");
        }

        if (!hasExpectedExecutorId(mesosResourcePool.getOffer())) {
            return fail(this,
                    "Offer does not contain the needed Executor ID: '%s'",
                    executorInfo.get().getExecutorId().getValue());
        }

        Protos.ExecutorID newExecutorId;
        String passMsgFormat;
        if (executorInfo.isPresent()) {
            newExecutorId = executorInfo.get().getExecutorId();
            passMsgFormat = "Offer contains the matching Executor ID: '%s'";
            podInfoBuilder.setExecutorBuilder(executorInfo.get().toBuilder());
        } else {
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            newExecutorId = CommonIdUtils.toExecutorId(executorBuilder.getName());
            passMsgFormat = "No Executor ID required, generated: '%s'";
            executorBuilder.setExecutorId(newExecutorId);
        }
        return pass(this, passMsgFormat, newExecutorId.getValue());
    }

    private boolean hasExpectedExecutorId(Protos.Offer offer) {
        if (!executorInfo.isPresent()) {
            return true;
        }

        for (Protos.ExecutorID execId : offer.getExecutorIdsList()) {
            if (execId.equals(executorInfo.get().getExecutorId())) {
                return true;
            }
        }

        return false;
    }
}
