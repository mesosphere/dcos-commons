package org.apache.mesos.scheduler.plan;

import java.util.List;

/**
 * Defines the interface for a execution Stage.
 * Plans contain Phases which contain Blocks.
 */
public interface Stage extends Completable {
    List<? extends Phase> getPhases();
    List<String> getErrors();
}
