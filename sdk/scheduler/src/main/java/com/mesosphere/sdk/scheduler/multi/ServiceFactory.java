package com.mesosphere.sdk.scheduler.multi;

import com.mesosphere.sdk.scheduler.AbstractScheduler;

/**
 * Callback object which constructs (or reconstructs) a service based on its name/id and context data. Used in
 * conjunction with a {@link ServiceStore} to reconstruct active services following a restart of the scheduler process.
 *
 * Context data is provided by the developer, and may contain e.g. configuration info for the service. Context data is
 * limited to 100KB (100 * 1024 bytes).
 */
public interface ServiceFactory {

    /**
     * Returns a constructed service in the form of an {@link AbstractScheduler}. It should parse any
     * application-specific information from the provided {@code context} and use that information to build the
     * {@link AbstractScheduler} using a {@code SchedulerBuilder}. An implementation of this interface must be provided
     * by the developer.
     *
     * @param context nullable context data for the service, as originally passed to {@link ServiceStore#put(byte[])}
     * @throws Exception if (re)constructing the service failed, for example if the {@code context} could not be parsed
     */
    public AbstractScheduler buildService(byte[] context) throws Exception;
}
