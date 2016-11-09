package org.apache.mesos.specification;

import org.apache.mesos.offer.constrain.PlacementRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PodSpec {
    String getType();
    Integer getCount();
    Optional<String> getUser();
    List<TaskSpec> getTasks();
    Collection<ResourceSet> getResources();
    Optional<PlacementRule> getPlacementRule();

    static String getName(PodSpec podSpec, int index) {
        return podSpec.getType() + "-" + index;
    }
}