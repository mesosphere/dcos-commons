package org.apache.mesos.specification;

import java.time.Duration;

/**
 * Created by gabriel on 11/7/16.
 */
public interface HealthCheckSpec {
    String getCommand();
    Integer getMaxConsecutiveFailures();
    Duration getDelay();
    Duration getInterval();
    Duration getTimeout();
    Duration getGracePeriod();
}
