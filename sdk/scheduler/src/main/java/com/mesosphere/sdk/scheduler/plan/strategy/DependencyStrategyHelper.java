package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.*;
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

    public DependencyStrategyHelper() {
        this(Collections.emptyList());
    }

    public DependencyStrategyHelper(Collection<C> elements) {
        this.dependencies = new HashMap<>();
        elements.forEach(element -> dependencies.put(element, new HashSet<>()));
    }

    public void addElement(C element) throws InvalidDependencyException {
        if (dependencies.get(element) != null) {
            throw new InvalidDependencyException("Attempted to overwrite previously added element: " + element);
        }

        dependencies.put(element, new HashSet<>());
    }

    public void addDependency(C child, C parent) {
        Set<C> deps = dependencies.get(child);
        if (deps == null) {
            deps = new HashSet<>();
        }

        if (dependencies.get(parent) == null) {
            dependencies.put(parent, new HashSet<>());
        }

        deps.add(parent);
        dependencies.put(child, deps);
    }

    public Collection<C> getCandidates(boolean isInterrupted, Collection<PodInstanceRequirement> dirtyAssets) {
        if (isInterrupted) {
            return Collections.emptyList();
        }
        return dependencies.entrySet().stream()
                .filter(entry -> entry.getKey().isEligible(dirtyAssets))
                .filter(entry -> dependenciesFulfilled(entry.getValue()))
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());
    }

    public Map<C, Set<C>> getDependencies() {
        return dependencies;
    }

    private static <C extends Element> boolean dependenciesFulfilled(Set<C> deps) {
        if (deps.isEmpty()) {
            return true;
        } else {
            return deps.stream().allMatch(c -> c.isComplete());
        }
    }

    /**
     * An {@link InvalidDependencyException} is thrown when an attempt generate an invalid dependency occurs.
     */
    public static class InvalidDependencyException extends Exception {
        public InvalidDependencyException(String message) {
            super(message);
        }
    }
}
