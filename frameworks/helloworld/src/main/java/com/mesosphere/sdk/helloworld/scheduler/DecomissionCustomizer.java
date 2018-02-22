package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.Protos;

import java.util.*;

/**
 * Decommission customize for testing purposes.
 */
public class DecomissionCustomizer implements PlanCustomizer {
    public static final String CUSTOM_DECOMMISSION_STEP_NAME = "custom_decomission_step";

    public Plan updatePlan(Plan plan) {
        if (!plan.isDecommissionPlan()) {
            return plan;
        }

        List<Phase> updatedPhases = new ArrayList<>();
        for (Phase phase : plan.getChildren()) {
            List<Step> steps = new ArrayList<>();
            steps.add(getCustomDecommissionStep());
            steps.addAll(phase.getChildren());

            updatedPhases.add(
                    new DefaultPhase(
                            phase.getName(),
                            steps,
                            new SerialStrategy<>(),
                            Collections.emptyList()));
        }

        return new DefaultPlan(plan.getName(), updatedPhases, new ParallelStrategy<>());
    }

    private Step getCustomDecommissionStep() {
        return new Step() {
            Status status = Status.PENDING;
            Object lock = new Object();

            @Override
            public Optional<PodInstanceRequirement> start() {
                synchronized (lock) {
                    status = Status.COMPLETE;
                }

                return Optional.empty();
            }

            @Override
            public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
                return Optional.empty();
            }

            @Override
            public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
               // Intentionally empty
            }

            @Override
            public UUID getId() {
                return UUID.randomUUID();
            }

            @Override
            public String getName() {
                return CUSTOM_DECOMMISSION_STEP_NAME;
            }

            @Override
            public Status getStatus() {
                return status;
            }

            @Override
            public void update(Protos.TaskStatus status) {
                // Intentionally empty
            }

            @Override
            public void restart() {
                synchronized (lock) {
                    status = Status.PENDING;
                }
            }

            @Override
            public void forceComplete() {
                synchronized (lock) {
                    status = Status.COMPLETE;
                }
            }

            @Override
            public List<String> getErrors() {
                return Collections.emptyList();
            }

            @Override
            public void interrupt() {
                synchronized (lock) {
                    status = Status.WAITING;
                }
            }

            @Override
            public void proceed() {
                synchronized (lock) {
                    status = Status.PENDING;
                }
            }

            @Override
            public boolean isInterrupted() {
                return status == Status.WAITING;
            }
        };
    }
}
