package com.mesosphere.sdk.scheduler;

import org.apache.mesos.SchedulerDriver;

/**
 * Interface that scheduler drivers for the Mesos V1 API should implement. Although the notion of a scheduler driver is
 * not required with the V1 interface, this matches existing usage patterns for the sake of minimal change.
 */
public interface V1SchedulerDriver extends SchedulerDriver {
    // Empty for now -- when we add features, this will have them.
}
