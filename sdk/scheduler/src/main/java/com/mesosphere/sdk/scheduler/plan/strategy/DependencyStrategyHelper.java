package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.PlanUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@link DependencyStrategyHelper} exists to aid in the construction of {@link Strategy} objects which describe a
 * set of dependencies between constituent elements.
 *
 * @param <C> is the type of {@link Element}s to which the dependencies captured here apply.
 */
public class DependencyStrategyHelper<C extends Element> {
  /**
   * Mapping of elements to their prerequisites which must be {@link Element#isComplete()}.
   */
  private final Map<C, Set<C>> dependencies;

  public DependencyStrategyHelper(Collection<C> elements) {
    this.dependencies = new HashMap<>();
    elements.forEach(element -> dependencies.put(element, new HashSet<>()));
  }

  private static <C extends Element> boolean dependenciesFulfilled(Set<C> deps) {
    return deps.isEmpty() || deps.stream().allMatch(Element::isComplete);
  }

  /**
   * Marks a direct dependency between two nodes, where {@code child} depends on {@code parent}.
   * <p>
   * <p>NOTE: This helper does NOT handle any inferred/chained dependencies. It is the responsibility of the caller to
   * explicitly add each dependency directly. For example, given c->b->a, the caller MUST explicitly specify c->b,
   * b->a, AND c->a as dependencies.
   */
  public void addDependency(C child, C parent) {
    // Ensure parent element is listed:
    addElement(parent);

    // Update dependencies in child:
    Set<C> deps = dependencies.get(child);
    if (deps == null) {
      deps = new HashSet<>();
    }
    deps.add(parent);
    dependencies.put(child, deps);
  }

  /**
   * Adds an element to be considered as a candidate. This may be used to add standalone entries which should be
   * considered but which don't have any dependencies. Calling this is NOT necessary for any elements which were added
   * (as child or as parent) via {@link #addDependency(Element, Element)}, or which were passed via the constructor.
   */
  public void addElement(C element) {
    dependencies.computeIfAbsent(element, k -> new HashSet<>());
  }

  public Collection<C> getCandidates(
      boolean isInterrupted,
      Collection<PodInstanceRequirement> dirtyAssets)
  {
    if (isInterrupted) {
      return Collections.emptyList();
    }
    return dependencies.entrySet().stream()
        .filter(entry ->
            PlanUtils.isEligible(entry.getKey(), dirtyAssets) &&
                dependenciesFulfilled(entry.getValue())
        )
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }
}
