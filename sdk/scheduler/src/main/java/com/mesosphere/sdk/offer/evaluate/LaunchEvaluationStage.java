package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;

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
        taskBuilder.setLabels(new SchedulerLabelWriter(taskBuilder)
            .setOfferAttributes(offer)
            .setType(podInfoBuilder.getOfferRequirement().getType())
            .setIndex(podInfoBuilder.getOfferRequirement().getIndex())
            .setHostname(offer)
            .toLabels());
        if (executorBuilder.isPresent()) {
            taskBuilder.setExecutor(executorBuilder.get());
        }

        return pass(
                this,
                Arrays.asList(new LaunchOfferRecommendation(offer, taskBuilder.build())),
                "Added launch information to offer requirement");
    }
}
