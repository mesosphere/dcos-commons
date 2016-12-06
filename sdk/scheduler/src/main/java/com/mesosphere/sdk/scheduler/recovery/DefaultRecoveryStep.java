package com.mesosphere.sdk.scheduler.recovery;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.scheduler.plan.DefaultStep;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.specification.PodInstance;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * {@code DefaultRecoveryStep} is an extension of {@link DefaultStep} meant for use with
 * {@link DefaultRecoveryPlanManager}. It is responsible for following:
 * 1. Encapsulating {@link RecoveryRequirement}
 * 2. Updating launchHappened event.
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public class DefaultRecoveryStep extends DefaultStep {
    private LaunchConstrainer launchConstrainer;
    private RecoveryRequirement recoveryRequirement;

    public DefaultRecoveryStep(
            String name,
            Status status,
            PodInstance podInstance,
            RecoveryRequirement recoveryRequirement,
            LaunchConstrainer launchConstrainer) {
        super(
                name,
                Optional.of(recoveryRequirement.getOfferRequirement()),
                status,
                podInstance,
                Collections.emptyList());
        this.launchConstrainer = launchConstrainer;
        this.recoveryRequirement = recoveryRequirement;
    }

    @Override
    public void updateOfferStatus(Collection<Protos.Offer.Operation> operations) {
        super.updateOfferStatus(operations);
        if (CollectionUtils.isNotEmpty(operations)) {
            launchConstrainer.launchHappened(operations.iterator().next(), recoveryRequirement.getRecoveryType());
        }
    }

    public RecoveryRequirement getRecoveryRequirement() {
        return recoveryRequirement;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " RecoveryType: " + recoveryRequirement.getRecoveryType().name();
    }
}
