package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import com.mesosphere.sdk.specification.util.RLimit;

import javax.validation.constraints.Size;
import javax.validation.constraints.NotNull;
import java.util.Collection;

/**
 * Default implementation of an {@link RLimitSpec}.
 */
public class DefaultRLimitSpec implements RLimitSpec {
    @NotNull
    @Size(min = 1)
    private final Collection<RLimit> rlimits;

    @JsonCreator
    public DefaultRLimitSpec(@JsonProperty("rlimits") Collection<RLimit> rlimits) {
        this.rlimits = rlimits;
    }

    @Override
    public Collection<RLimit> getRLimits() {
        return rlimits;
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
