package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;

import java.util.Optional;

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
    public void evaluate(
            MesosResourcePool mesosResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) {
        Optional<ExecutorRequirement> executorRequirement = offerRequirement.getExecutorRequirementOptional();
        Protos.Offer offer = mesosResourcePool.getOffer();
        Protos.TaskInfo.Builder taskBuilder = offerRequirement.getTaskRequirement(taskName).getTaskInfo().toBuilder();

        // Store metadata in the TaskInfo for later access by placement constraints:
        if (executorRequirement.isPresent()) {
            taskBuilder.setExecutor(executorRequirement.get().getExecutorInfo());
        }
        taskBuilder = CommonTaskUtils.setOfferAttributes(taskBuilder, offer);
        taskBuilder = CommonTaskUtils.setType(taskBuilder, offerRequirement.getType());
        taskBuilder = CommonTaskUtils.setIndex(taskBuilder, offerRequirement.getIndex());
        taskBuilder = CommonTaskUtils.setHostname(taskBuilder, offer);

        Protos.TaskInfo taskInfo = CommonTaskUtils.packTaskInfo(taskBuilder.build());
        offerRequirement.updateTaskRequirement(taskBuilder.getName(), taskInfo);
        offerRecommendationSlate.addLaunchRecommendation(new LaunchOfferRecommendation(offer, taskInfo));
    }
}
