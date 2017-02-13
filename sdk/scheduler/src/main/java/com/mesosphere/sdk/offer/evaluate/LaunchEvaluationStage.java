package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Optional;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.*;

/**
 * This class sets pod metadata on a {@link org.apache.mesos.Protos.TaskInfo} in an {@link OfferRequirement}, ensuring
 * that this metadata is available in the task's environment and creating an {@link LaunchOfferRecommendation}.
 */
public class LaunchEvaluationStage implements OfferEvaluationStage {
    private final String taskName;

    public LaunchEvaluationStage(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        Optional<Protos.ExecutorInfo.Builder> executorBuilder = podInfoBuilder.getExecutorBuilder();
        Protos.Offer offer = mesosResourcePool.getOffer();
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);

        // Store metadata in the TaskInfo for later access by placement constraints:
        taskBuilder = CommonTaskUtils.setOfferAttributes(taskBuilder, offer);
        taskBuilder = CommonTaskUtils.setType(taskBuilder, podInfoBuilder.getOfferRequirement().getType());
        taskBuilder = CommonTaskUtils.setIndex(taskBuilder, podInfoBuilder.getOfferRequirement().getIndex());
        taskBuilder = CommonTaskUtils.setHostname(taskBuilder, offer);
        if (executorBuilder.isPresent()) {
            taskBuilder.setExecutor(executorBuilder.get());
        }

        return pass(
                this,
                Arrays.asList(new LaunchOfferRecommendation(offer, taskBuilder.build())),
                "Added launch information to offer requirement");
    }
}
