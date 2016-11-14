package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.mesos.offer.constrain.PlacementRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Specification for a Pod.
 */
@JsonDeserialize(as = DefaultPodSpec.class)
public interface PodSpec {
    @JsonProperty("type")
    String getType();

    @JsonProperty("count")
    Integer getCount();

    @JsonProperty("user")
    Optional<String> getUser();

    @JsonProperty("task_specs")
    List<TaskSpec> getTasks();

    @JsonProperty("resource_sets")
    Collection<ResourceSet> getResources();

    @JsonProperty("placement_rule")
    Optional<PlacementRule> getPlacementRule();

    @JsonIgnore
    static String getName(PodSpec podSpec, int index) {
        return podSpec.getType() + "-" + index;
    }
}
