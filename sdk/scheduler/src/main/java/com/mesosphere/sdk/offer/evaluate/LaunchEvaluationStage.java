package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LegacyLaunchOfferRecommendation;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import org.apache.mesos.Protos;

import java.util.Arrays;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class sets pod metadata on a {@link org.apache.mesos.Protos.TaskInfo}, ensuring
 * that this metadata is available in the task's environment and creating a {@link LegacyLaunchOfferRecommendation}.
 */
public class LaunchEvaluationStage implements OfferEvaluationStage {
    private final String taskName;
    private final boolean shouldLaunch;
    private final boolean useDefaultExecutor;

    public LaunchEvaluationStage(String taskName) {
        this(taskName, true, true);
    }
    public LaunchEvaluationStage(String taskName, boolean shouldLaunch, boolean useDefaultExecutor) {
        this.taskName = taskName;
        this.shouldLaunch = shouldLaunch;
        this.useDefaultExecutor = useDefaultExecutor;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
        Protos.Offer offer = mesosResourcePool.getOffer();
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
        taskBuilder.setTaskId(CommonIdUtils.toTaskId(taskBuilder.getName()));

        // Store metadata in the TaskInfo for later access by placement constraints:
        TaskLabelWriter writer = new TaskLabelWriter(taskBuilder);
        writer.setOfferAttributes(offer)
                .setType(podInfoBuilder.getType())
                .setIndex(podInfoBuilder.getIndex())
                .setHostname(offer);

        if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
            writer.setRegion(offer.getDomain().getFaultDomain().getRegion());
            writer.setZone(offer.getDomain().getFaultDomain().getZone());
        }

        taskBuilder.setLabels(writer.toProto());

        if (!useDefaultExecutor) {
            taskBuilder.setExecutor(executorBuilder);
        }

        return pass(
                this,
                Arrays.asList(new LegacyLaunchOfferRecommendation(
                        offer, taskBuilder.build(), shouldLaunch)),
                "Added launch information to offer requirement")
                .build();
    }
}
