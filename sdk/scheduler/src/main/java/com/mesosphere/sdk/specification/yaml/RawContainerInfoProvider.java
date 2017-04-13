package com.mesosphere.sdk.specification.yaml;


/**
 * Specification for an entity that defines a container's settings.
 */
public interface RawContainerInfoProvider {
    WriteOnceLinkedHashMap<String, RawNetwork> getNetworks();
    WriteOnceLinkedHashMap<String, RawRLimit> getRLimits();
    String getImage();
}
