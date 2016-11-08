package org.apache.mesos.specification;

import org.apache.mesos.offer.constrain.PlacementRule;

import java.util.Collection;
import java.util.Optional;

/**
 * Created by gabriel on 11/7/16.
 */
public interface PodSpec {
    String getName();
    Collection<TaskSpec> getTasks();
    Collection<ResourceSet> getResources();
    Optional<PlacementRule> getPlacementRule();
}
