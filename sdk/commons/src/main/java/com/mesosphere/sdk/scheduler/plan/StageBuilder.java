package com.mesosphere.sdk.scheduler.plan;

import java.util.List;

/**
 * Interface for objects that build Plans.
 */
public interface StageBuilder {

    void addPhase(Phase phase);

    void setPhases(List<Phase> phases);
}
