package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@link DependencyStrategyHelper} exists to aid in the construction of {@link Strategy} objects which wish to
 * describe a set of dependencies between constituent elements.
 *
 * @param <C> is the type of {@link Element}s to which the dependencies captured here apply.
 */
public class DependencyStrategyHelper<C extends Element> {
    private final Map<C, Set<C>> dependencies;
    private final Collection<? extends Element> elements;

    public DependencyStrategyHelper() {
        this(Collections.emptyList());
    }

    public DependencyStrategyHelper(Collection<? extends Element> elements) {
        this.elements = elements;
        this.dependencies = new HashMap<>();
        elements.forEach(child -> dependencies.put((C) child, new HashSet<>()));
    }

    public DependencyStrategyHelper(Element parentElement) {
        this(parentElement.getChildren());
    }

    public void addElement(C element) throws InvalidDependencyException {
        if (dependencies.get(element) != null) {
            throw new InvalidDependencyException("Attempted to overwrite previously added element: " + element);
        }

        dependencies.put(element, new HashSet<C>());
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

    public Collection<C> getCandidates(Collection<String> dirtyAssets) {
        Collection<C> candidates = dependencies.entrySet().stream()
                .filter(entry -> !entry.getKey().getStrategy().isInterrupted())
                .filter(entry -> !dirtyAssets.contains(entry.getKey().getName()))
                .filter(entry -> entry.getKey().isPending())
                .filter(entry -> dependenciesFulfilled(entry.getValue()))
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());

        return candidates;
    }

    public Map<C, Set<C>> getDependencies() {
        return dependencies;
    }

    private boolean dependenciesFulfilled(Set<C> deps) {
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
