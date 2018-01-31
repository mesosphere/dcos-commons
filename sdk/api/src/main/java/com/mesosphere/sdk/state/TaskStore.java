package com.mesosphere.sdk.state;

import java.util.Optional;


/**
 * Developer-facing functions for task state retrieval.
 */
public interface TaskStore {

    /**
     * Retrieves the last known IP for the provided task name, or an empty Optional if no such IP was found.
     */
    public Optional<String> getTaskIp(String taskName);
}
