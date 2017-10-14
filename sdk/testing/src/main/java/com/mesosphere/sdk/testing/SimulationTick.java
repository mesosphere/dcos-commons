package com.mesosphere.sdk.testing;

/**
 * A step in a cluster simulation.
 */
public interface SimulationTick {

    /**
     * Returns a description of this step for debugging by the user.
     */
    public String getDescription();
}
