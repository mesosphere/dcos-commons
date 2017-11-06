package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * This interface defines the required methods for generic application of a PlacementRule which depends on the presence
 * of some key (e.g. attribute, hostname, region, zone ...).
 */
public abstract class StringMatcherRule implements PlacementRule {
    public abstract Collection<String> getKeys(Protos.Offer offer);

    public boolean isAcceptable(
            StringMatcher matcher,
            Protos.Offer offer,
            PodInstance podInstance,
            Collection<Protos.TaskInfo> tasks) {
        for (String key : getKeys(offer)) {
            if (matcher.matches(key)) {
                return true;
            }
        }

        return false;
    }
}
