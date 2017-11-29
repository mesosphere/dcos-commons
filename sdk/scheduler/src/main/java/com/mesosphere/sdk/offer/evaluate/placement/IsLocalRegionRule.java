package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Collection;

/**
 * This rule passes if the Offer is on the local region.
 */
public class IsLocalRegionRule implements PlacementRule {
    private static Protos.DomainInfo localDomain;

    public static void setLocalDomain(Protos.DomainInfo domainInfo) {
        localDomain = domainInfo;
    }

    @Override
    public EvaluationOutcome filter(Protos.Offer offer, PodInstance podInstance, Collection<Protos.TaskInfo> tasks) {
        boolean regionPresent = offer.hasDomain() && offer.getDomain().hasFaultDomain();
        if (!regionPresent) {
            return EvaluationOutcome.pass(
                    this,
                    "The Offer has no Region, so it is in the local region.")
                    .build();
        }

        if (localDomain == null || !localDomain.hasFaultDomain()) {
            return EvaluationOutcome.pass(
                    this,
                    "The Master has not reported a FaultDomain on registration, " +
                            "so all offers are presumed to be in local region.")
                    .build();
        }

        Protos.DomainInfo.FaultDomain.RegionInfo offerRegion = offer.getDomain().getFaultDomain().getRegion();
        Protos.DomainInfo.FaultDomain.RegionInfo localRegion = localDomain.getFaultDomain().getRegion();

        if (offerRegion.equals(localRegion)) {
            return EvaluationOutcome.pass(
                    this,
                    "The offer is in the local region: '%s'",
                    localRegion.getName())
                    .build();
        } else {
            return EvaluationOutcome.fail(
                    this,
                    "The offer is in region: '%s' NOT the local region: '%s'",
                    offerRegion.getName(),
                    localRegion.getName())
                    .build();
        }
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.REGION);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return "IsLocalRegionRule";
    }
}
