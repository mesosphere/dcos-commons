package com.mesosphere.sdk.specification.yaml;

import java.util.LinkedHashMap;

/**
 * Specification for an entity that defines a container's settings.
 */
public interface RawContainerInfoProvider {
    public LinkedHashMap<String, RawNetwork> getNetworks();
    public LinkedHashMap<String, RawRLimit> getRLimits();
    public String getImage();
}
