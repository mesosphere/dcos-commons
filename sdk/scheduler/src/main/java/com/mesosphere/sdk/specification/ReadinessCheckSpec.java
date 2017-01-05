package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * ReadinessCheck specification.
 */
@JsonDeserialize(as = DefaultReadinessCheckSpec.class)
public interface ReadinessCheckSpec {
    String getCommand();

    Integer getDelay();

    Integer getInterval();

    Integer getTimeout();

    Integer getGracePeriod();
}
