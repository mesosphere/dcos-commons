package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.plan.DefaultStep;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.recovery.RecoveryType;

import java.util.Collection;
import java.util.Collections;

/**
 * {@code DefaultRecoveryStep} is an extension of {@link DefaultStep} meant for use with
 * {@link DefaultRecoveryPlanManager}.
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public class DefaultRecoveryStep extends DefaultStep {
    private LaunchConstrainer launchConstrainer;
    private RecoveryType recoveryType;

    public DefaultRecoveryStep(
            String name,
            Status status,
            PodInstance podInstance,
            Collection<String> tasksToLaunch,
            RecoveryType recoveryType,
            LaunchConstrainer launchConstrainer) {
        super(
                name,
                status,
                podInstance,
                tasksToLaunch,
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
}
