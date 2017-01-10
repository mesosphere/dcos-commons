package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mesosphere.sdk.offer.constrain.PlacementRule;

import java.net.URI;
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

    @JsonProperty("container")
    Optional<ContainerSpec> getContainer();

    @JsonProperty("uris")
    Collection<URI> getUris();

    @JsonProperty("user")
    Optional<String> getUser();

    @JsonProperty("task-specs")
    List<TaskSpec> getTasks();

    @JsonProperty("resource-sets")
    Collection<ResourceSet> getResources();

    @JsonProperty("placement-rule")
    Optional<PlacementRule> getPlacementRule();

    @JsonIgnore
    static String getName(PodSpec podSpec, int index) {
        return podSpec.getType() + "-" + index;
    }
}
