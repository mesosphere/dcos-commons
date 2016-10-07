package org.apache.mesos.scheduler.recovery;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.plan.DefaultBlock;
import org.apache.mesos.scheduler.plan.Status;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;

import java.util.Collection;

/**
 * DefaultRecoveryBlock.
 */
public class DefaultRecoveryBlock extends DefaultBlock {
    private LaunchConstrainer launchConstrainer;
    private RecoveryRequirement recoveryRequirement;

    public DefaultRecoveryBlock(
            String name,
            OfferRequirement offerRequirement,
            Status status,
            RecoveryRequirement recoveryRequirement,
            LaunchConstrainer launchConstrainer) {
        super(name, offerRequirement, status);
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
