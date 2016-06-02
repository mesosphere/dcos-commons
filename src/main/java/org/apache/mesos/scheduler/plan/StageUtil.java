package org.apache.mesos.scheduler.plan;

import com.google.common.collect.Collections2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.scheduler.plan.filter.IncompletePredicate;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Provides a number of utility functions for working with plans, phases and blocks.
 */
public class StageUtil {

  @SuppressWarnings("unchecked")
  public static Collection<? extends Block> getAllIncompleteBlocks(Collection<? extends Block> blocks) {
    if (CollectionUtils.isEmpty(blocks)) {
      return Collections.EMPTY_LIST;
    }
    return Collections2.filter(blocks, new IncompletePredicate());
  }

  @SuppressWarnings("unchecked")
  public static Collection<? extends Phase> getAllIncompletePhases(Collection<? extends Phase> phases) {
    if (CollectionUtils.isEmpty(phases)) {
      return Collections.EMPTY_LIST;
    }
    return Collections2.filter(phases, new IncompletePredicate());
  }

  public static Block getBlock(UUID phaseId, UUID blockId, Stage stage) {
    Phase phase = getPhase(phaseId, stage);
    return (phase != null) ? phase.getBlock(blockId) : null;
  }

  public static Phase getPhase(UUID phaseId, Stage stage) {
    for (Phase phase : stage.getPhases()) {
      if (phaseId.equals(phase.getId())) {
        return phase;
      }
    }
    return null;
  }
}
