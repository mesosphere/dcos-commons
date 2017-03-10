package com.mesosphere.sdk.specification.yaml;

import java.util.LinkedHashMap;

public interface RawContainerInfoProvider {
    public LinkedHashMap<String, RawNetwork> getNetworks();
    public LinkedHashMap<String, RawRLimit> getRLimits();
    public String getImage();
}
