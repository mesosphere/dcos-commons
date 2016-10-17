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
    private final Element parentElement;

    public DependencyStrategyHelper(Element parentElement) {
        this.parentElement = parentElement;
        this.dependencies = new HashMap<>();

        parentElement.getChildren().forEach(child -> dependencies.put((C) child, new HashSet<>()));
    }

    public void addDependency(C parent, C child) {
        Set<C> deps = dependencies.get(parent);
        if (deps == null) {
            throw new RuntimeException(
                    String.format("Attempted to add dependency to unknown element. parent: %s, child: %s",
                            parent, child));
        }

        deps.add(child);
        dependencies.put(parent, deps);
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

    private boolean dependenciesFulfilled(Set<C> deps) {
        if (deps.isEmpty()) {
            return true;
        } else {
            return deps.stream().allMatch(c -> c.isComplete());
        }
    }
}
