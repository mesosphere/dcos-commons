package com.mesosphere.sdk.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;

/**
 * This class is a default implementation of the {@link PodInstance} interface.
 */
public class DefaultPodInstance implements PodInstance {
    private final PodSpec podSpec;
    private final Integer index;

    public DefaultPodInstance(PodSpec podSpec, Integer index) {
        this.podSpec = podSpec;
        this.index = index;
    }

    @Override
    public PodSpec getPod() {
        return podSpec;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
