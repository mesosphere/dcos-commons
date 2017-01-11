package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.plan.DefaultStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * An extension of {@link DefaultStep} meant for use with {@link DefaultRecoveryPlanManager}.
 */
public class DefaultRecoveryStep extends DefaultStep {

    private final LaunchConstrainer launchConstrainer;
    private final RecoveryType recoveryType;

    public DefaultRecoveryStep(
            String name,
            Status status,
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            RecoveryType recoveryType,
            LaunchConstrainer launchConstrainer) {
        super(name,
                status,
                recoveryType == RecoveryType.PERMANENT ?
                        PodInstanceRequirement.createPermanentReplacement(podInstance, tasksToLaunch) :
                        PodInstanceRequirement.create(podInstance, tasksToLaunch),
                Collections.emptyList());
        this.launchConstrainer = launchConstrainer;
        this.recoveryType = recoveryType;
    }

    @Override
    public void updateOfferStatus(Collection<Protos.Offer.Operation> operations) {
        super.updateOfferStatus(operations);
        if (CollectionUtils.isNotEmpty(operations)) {
            launchConstrainer.launchHappened(operations.iterator().next(), recoveryType);
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
