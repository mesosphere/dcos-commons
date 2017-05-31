package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import org.apache.mesos.Protos;

import java.util.Arrays;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class sets pod metadata on a {@link org.apache.mesos.Protos.TaskInfo}, ensuring
 * that this metadata is available in the task's environment and creating a {@link LaunchOfferRecommendation}.
 */
public class LaunchEvaluationStage implements OfferEvaluationStage {
    private final String taskName;
    private final boolean shouldLaunch;

    public LaunchEvaluationStage(String taskName) {
        this(taskName, true);
    }
    public LaunchEvaluationStage(String taskName, boolean shouldLaunch) {
        this.taskName = taskName;
        this.shouldLaunch = shouldLaunch;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        // FIX: no lonegr optional on podinfobuilder
        Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
        Protos.Offer offer = mesosResourcePool.getOffer();
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
        taskBuilder.setTaskId(CommonIdUtils.toTaskId(taskBuilder.getName()));

        // Store metadata in the TaskInfo for later access by placement constraints:
        taskBuilder.setLabels(new SchedulerLabelWriter(taskBuilder)
            .setOfferAttributes(offer)
            .setType(podInfoBuilder.getType())
            .setIndex(podInfoBuilder.getIndex())
            .setHostname(offer)
            .toProto());

        return pass(
                this,
                null,
                Arrays.asList(new LaunchOfferRecommendation(
                        offer, taskBuilder.build(), executorBuilder.build(), shouldLaunch)),
                "Added launch information to offer requirement");
    }
}
