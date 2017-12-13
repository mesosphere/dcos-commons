package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;

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

    @JsonProperty("allow-decommission")
    Boolean getAllowDecommission();

    @JsonProperty("image")
    Optional<String> getImage();

    @JsonProperty("networks")
    Collection<NetworkSpec> getNetworks();

    @JsonProperty("rlimits")
    Collection<RLimitSpec> getRLimits();

    @JsonProperty("uris")
    Collection<URI> getUris();

    @JsonProperty("user")
    Optional<String> getUser();

    @JsonProperty("task-specs")
    List<TaskSpec> getTasks();

    @JsonProperty("placement-rule")
    Optional<PlacementRule> getPlacementRule();

    @JsonProperty("volumes")
    Collection<VolumeSpec> getVolumes();

    @JsonProperty("pre-reserved-role")
    String getPreReservedRole();

    @JsonProperty("secrets")
    Collection<SecretSpec> getSecrets();

    @JsonProperty("share-pid-namespace")
    Boolean getSharePidNamespace();

    @JsonIgnore
    static String getName(PodSpec podSpec, int index) {
        return podSpec.getType() + "-" + index;
    }
}
