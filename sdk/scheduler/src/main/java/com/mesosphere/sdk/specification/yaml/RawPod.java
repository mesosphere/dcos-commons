package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.Constants;
import org.apache.commons.collections.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Raw YAML pod.
 */
public class RawPod implements RawContainerInfoProvider {

    private final String placement;
    private final Integer count;
    private final RawContainer container;
    private final String image;
    private final WriteOnceLinkedHashMap<String, RawNetwork> networks;
    private final WriteOnceLinkedHashMap<String, RawRLimit> rlimits;
    private final String user;
    private final Collection<String> uris;
    private final WriteOnceLinkedHashMap<String, RawTask> tasks;
    private final WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets;
    private final RawVolume volume;
    private final WriteOnceLinkedHashMap<String, RawVolume> volumes;
    private final String preReservedRole;
    private final WriteOnceLinkedHashMap<String, RawSecret> secrets;

    private RawPod(
            @JsonProperty("resource-sets") WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets,
            @JsonProperty("placement") String placement,
            @JsonProperty("count") Integer count,
            @JsonProperty("container") RawContainer container,
            @JsonProperty("image") String image,
            @JsonProperty("networks") WriteOnceLinkedHashMap<String, RawNetwork> networks,
            @JsonProperty("rlimits") WriteOnceLinkedHashMap<String, RawRLimit> rlimits,
            @JsonProperty("uris") Collection<String> uris,
            @JsonProperty("tasks") WriteOnceLinkedHashMap<String, RawTask> tasks,
            @JsonProperty("user") String user,
            @JsonProperty("volume") RawVolume volume,
            @JsonProperty("volumes") WriteOnceLinkedHashMap<String, RawVolume> volumes,
            @JsonProperty("pre-reserved-role") String preReservedRole,
            @JsonProperty("secrets") WriteOnceLinkedHashMap<String, RawSecret> secrets) {
        this.placement = placement;
        this.count = count;
        this.container = container;
        this.image = image;
        this.networks = networks == null ? new WriteOnceLinkedHashMap<>() : networks;
        this.rlimits = rlimits == null ? new WriteOnceLinkedHashMap<>() : rlimits;
        this.user = user;
        this.uris = uris;
        this.tasks = tasks;
        this.resourceSets = resourceSets;
        this.volume = volume;
        this.volumes = volumes == null ? new WriteOnceLinkedHashMap<>() : volumes;
        this.preReservedRole = preReservedRole == null ? Constants.ANY_ROLE : preReservedRole;
        this.secrets = secrets == null ? new WriteOnceLinkedHashMap<>() : secrets;

    }

    public String getPlacement() {
        return placement;
    }

    public Integer getCount() {
        return count;
    }

    public RawContainer getContainer() {
        return container;
    }

    public String getImage() {
        return image;
    }

    public WriteOnceLinkedHashMap<String, RawNetwork> getNetworks() {
        return networks;
    }

    public WriteOnceLinkedHashMap<String, RawRLimit> getRLimits() {
        return rlimits;
    }

    public LinkedHashMap<String, RawTask> getTasks() {
        return tasks;
    }

    public Collection<String> getUris() {
        return CollectionUtils.isEmpty(uris) ? Collections.emptyList() : uris;
    }

    public String getUser() {
        return user;
    }

    public WriteOnceLinkedHashMap<String, RawResourceSet> getResourceSets() {
        return resourceSets;
    }

    public RawVolume getVolume() {
        return volume;
    }

    public WriteOnceLinkedHashMap<String, RawVolume> getVolumes() {
        return volumes;
    }

    public String getPreReservedRole() {
        return preReservedRole;
    }

    public WriteOnceLinkedHashMap<String, RawSecret> getSecrets() {
        return secrets;
    }

}
