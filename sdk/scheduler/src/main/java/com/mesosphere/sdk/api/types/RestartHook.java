package com.mesosphere.sdk.api.types;

import java.util.Collection;

/**
 * Callback for notifying the service that a task restart or task replace is about to be performed. This hook allows
 * the developer to perform custom behavior before the restart or replace itself is performed.
 */
public interface RestartHook {

    /**
     * Notifies the hook that the provided {@code tasks} are about to be restarted or replaced. The hook should
     * determine based on the provided data whether any custom work is needed before the provided tasks are killed by
     * Mesos. The hook should only return once that work is completed (or has failed).
     *
     * @param tasks the task(s) to be restarted or replaced along with any associated statuses
     * @param replace whether the operation is a replacement ({@code true}) or an in-place restart ({@code false})
     * @return whether to proceed with the restart operation ({@code true}), or to abort the restart operation
     *     ({@code false})
     */
    public boolean notify(Collection<TaskInfoAndStatus> tasks, boolean replace);
}
