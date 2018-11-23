package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * {@code RandomStrategy} extends {@link Strategy}, by providing a random {@link Step}selection strategy.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public class RandomStrategy<C extends Element> extends InterruptibleStrategy<C> {

  @Override
  public Collection<C> getCandidates(
      Collection<C> elements,
      Collection<PodInstanceRequirement> dirtyAssets)
  {
    // No prerequisites configured, with random selection of one entry from the resulting
    // candidates:
    List<C> candidates = new ArrayList<>(
        new DependencyStrategyHelper<>(elements).getCandidates(isInterrupted(), dirtyAssets));
    Collections.shuffle(candidates);
    Optional<C> candidateOptional = candidates.stream().findFirst();

    return candidateOptional.map(Arrays::asList).orElse(Collections.emptyList());
  }

  @Override
  public String getName() {
    return "random";
  }

  public StrategyGenerator<C> getGenerator() {
    return new Generator<>();
  }

  /**
   * This class generates Strategy objects of the appropriate type.
   *
   * @param <C> is the type of {@link Element}s to which the Strategy applies.
   */
  public static class Generator<C extends Element> implements StrategyGenerator<C> {
    @Override
    public Strategy<C> generate(List<C> ignored) {
      return new RandomStrategy<>();
    }
  }
}
