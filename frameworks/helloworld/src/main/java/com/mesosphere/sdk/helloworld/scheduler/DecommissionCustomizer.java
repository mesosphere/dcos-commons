package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.*;
import org.apache.mesos.Protos.TaskStatus;

import java.util.*;

/**
 * An example of a plan customizer that updates the decommission plan, to be used in integration tests.
 *
 * The applied customization is to preface all phases in the decommission plan with a stub custom step. Other plans are
 * left as-is.
 */
public class DecommissionCustomizer implements PlanCustomizer {
    private static final String CUSTOM_DECOMMISSION_STEP_NAME = "custom_decommission_step";

    private final Optional<String> namespace;

    public DecommissionCustomizer(Optional<String> namespace) {
        this.namespace = namespace;
    }

    @Override
    public Plan updatePlan(Plan plan) {
        if (!plan.isDecommissionPlan()) {
            return plan;
        }

        List<Phase> updatedPhases = new ArrayList<>();
        for (Phase phase : plan.getChildren()) {
            List<Step> steps = new ArrayList<>();
            steps.add(new CustomStep(namespace));
            steps.addAll(phase.getChildren());
            updatedPhases.add(new DefaultPhase(phase.getName(), steps, phase.getStrategy(), phase.getErrors()));
        }
        return new DefaultPlan(plan.getName(), updatedPhases, plan.getStrategy());
    }

    /**
     * A no-op step that marks itself complete as soon as it's started.
     */
    private static class CustomStep extends AbstractStep {
        protected CustomStep(Optional<String> namespace) {
            super(CUSTOM_DECOMMISSION_STEP_NAME, namespace);
        }

        @Override
        public void start() {
            setStatus(Status.COMPLETE);
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
        public void update(TaskStatus status) {
            // Intentionally empty

        }

        @Override
        public List<String> getErrors() {
            return Collections.emptyList();
        }
    }
}
