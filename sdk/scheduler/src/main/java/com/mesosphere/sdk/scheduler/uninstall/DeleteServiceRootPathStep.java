package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.AbstractStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Step which deletes a framework from Zookeeper.
 */
public class DeleteServiceRootPathStep extends AbstractStep {

    private final StateStore stateStore;

    /**
     * Creates a new instance with the provided {@code stateStore} and initial {@code status}.
     */
    public DeleteServiceRootPathStep(StateStore stateStore, Status status) {
        super("zk", status);
        this.stateStore = stateStore;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        logger.info("Deleting service root path for framework...");
        stateStore.clearAllData();
        logger.info("Finished deleting service root path for framework");
        setStatus(Status.COMPLETE);
        return Optional.empty();
    }

    @Override
    public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
    }

    @Override
    public Optional<PodInstanceRequirement> getAsset() {
        return getPodInstanceRequirement();
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public void update(Protos.TaskStatus status) {
    }

}
