package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.DeploymentStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.StateStore;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * An extension of {@link DeploymentStep} meant for use with {@link DefaultRecoveryPlanManager}.
 */
public class DefaultRecoveryStep extends DeploymentStep {

    private final LaunchConstrainer launchConstrainer;
    private final RecoveryType recoveryType;
    private final StateStore stateStore;

    public DefaultRecoveryStep(
            String name,
            Status status,
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            RecoveryType recoveryType,
            LaunchConstrainer launchConstrainer,
            StateStore stateStore) {
        super(name,
                status,
                recoveryType == RecoveryType.PERMANENT ?
                        PodInstanceRequirement.createPermanentReplacement(podInstance, tasksToLaunch) :
                        PodInstanceRequirement.create(podInstance, tasksToLaunch),
                Collections.emptyList());
        this.recoveryType = recoveryType;
        this.launchConstrainer = launchConstrainer;
        this.stateStore = stateStore;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        if (recoveryType == RecoveryType.PERMANENT) {
            Collection<Protos.TaskInfo> taskInfos = TaskUtils.getPodTasks(
                    podInstanceRequirement.getPodInstance(),
                    stateStore);
            stateStore.storeTasks(TaskUtils.clearReservations(taskInfos));
        }
        return super.start();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
        super.updateOfferStatus(recommendations);
        for (OfferRecommendation recommendation : recommendations) {
            if (recommendation instanceof LaunchOfferRecommendation) {
                launchConstrainer.launchHappened((LaunchOfferRecommendation) recommendation, recoveryType);
            }
        }
    }

    public RecoveryType getRecoveryType() {
        return recoveryType;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " RecoveryType: " + recoveryType.name();
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
