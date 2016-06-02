package org.apache.mesos.scheduler.plan.filter;

import com.google.common.base.Predicate;
import org.apache.mesos.scheduler.plan.Completable;

/**
 * Used to provide a list of Completeables that are not finished.
 * This would be a list of blocks or phases.
 * ex. Collections2.filter(phases, new IncompletePredicate());
 */
public class IncompletePredicate implements Predicate<Completable> {

  @Override
  public boolean apply(Completable block) {
    return !block.isComplete();
  }
}
