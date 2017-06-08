package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement},
 * ensuring that it contains a sufficient amount or value of the supplied {@link Resource}, and creating a
 * {@link ReserveOfferRecommendation} or {@link UnreserveOfferRecommendation} where necessary.
 */
public class ResourceEvaluationStage implements OfferEvaluationStage {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected final String taskName;
    protected Optional<String> resourceId;
    protected ResourceSpec resourceSpec;

    public ResourceEvaluationStage(ResourceSpec resourceSpec, Optional<String> resourceId, String taskName) {
        this.resourceSpec = resourceSpec;
        this.resourceId = resourceId;
        this.taskName = taskName;
    }

    protected Optional<String> getTaskName() {
        return Optional.ofNullable(taskName);
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        EvaluationOutcome evaluationOutcome = OfferEvaluationUtils.evaluateSimpleResource(
                this,
                resourceSpec,
                resourceId,
                mesosResourcePool);
        if (!evaluationOutcome.isPassing()) {
            return evaluationOutcome;
        }

        Resource resource = ResourceBuilder.fromSpec(resourceSpec, resourceId)
                .setMesosResource(evaluationOutcome.getMesosResource().get())
                .build();
        setProtos(podInfoBuilder, resource);

        return evaluationOutcome;
    }

    public void setResourceId(Optional<String> resourceId) {
        this.resourceId = resourceId;
    }

    protected void setProtos(PodInfoBuilder podInfoBuilder, Resource resource) {
        if (getTaskName().isPresent()) {
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
            taskBuilder.addResources(resource);
        } else {
            Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
            executorBuilder.addResources(resource);
        }
    }

    protected String getSummary() {
        return String.format(
                "name: '%s', value: '%s', role: '%s', principal: '%s', resourceId: '%s'",
                resourceSpec.getName(),
                TextFormat.shortDebugString(resourceSpec.getValue()),
                resourceSpec.getRole(),
                resourceSpec.getPrincipal(),
                resourceId);
    }
}
