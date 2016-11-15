package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

/**
 * Raw YAML ResourceSet.
 */
public class RawResourceSet {
    String id;
    Collection<RawResource> resources;
    Collection<RawVolume> volumes;
    Collection<RawVip> vips;

    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Collection<RawResource> getResources() {
        return resources;
    }

    @JsonProperty("resources")
    public void setResources(Collection<RawResource> resources) {
        this.resources = resources;
    }

    public Collection<RawVolume> getVolumes() {
        return volumes;
    }

    @JsonProperty("volumes")
    public void setVolumes(Collection<RawVolume> volumes) {
        this.volumes = volumes;
    }

    public Collection<RawVip> getVips() {
        return vips;
    }

    @JsonProperty("vips")
    public void setVips(Collection<RawVip> vips) {
        this.vips = vips;
    }
}
