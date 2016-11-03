package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This class provides a default implementation of the PodSetSpecification interface.
 */
public class DefaultPodSetSpecification implements PodSetSpecification {
    private final String name;
    private final List<PodSpecification> podSpecifications;

    /**
     * Enforces placement contraints on the Pod.
     * @param podCount Number of pod instances to launch.
     * @param podName Name of the pod.
     * @param placement The placement constraint to to enforce.
     * @param taskSpecifications The tasks to run in the pod.
     * @return Specification of the pod set.
     */
    public static DefaultPodSetSpecification create(
            int podCount,
            String podName,
            Optional<PlacementRuleGenerator> placement,
            List<TaskSpecification> taskSpecifications) {

        List<PodSpecification> podSpecifications = new ArrayList<>();
        List<TaskSpecification> indexedTaskSpecifications = new ArrayList<>();

        for (int podIndex = 0; podIndex < podCount; podIndex++) {
            indexedTaskSpecifications.clear();
            for (TaskSpecification taskSpec : taskSpecifications) {
                indexedTaskSpecifications.add(
                        new DefaultTaskSpecification(
                                taskSpec.getName() + "-" + podIndex,
                                taskSpec.getType(),
                                taskSpec.getContainer(),
                                taskSpec.getCommand(),
                                taskSpec.getResources(),
                                taskSpec.getVolumes(),
                                taskSpec.getConfigFiles(),
                                placement,
                                taskSpec.getHealthCheck(),
                                podName
                        )
                );
            }
            podSpecifications.add(
                    new DefaultPodSpecification(podName + "-" + podIndex, indexedTaskSpecifications, placement)
            );
        }
        return new DefaultPodSetSpecification(podName, podSpecifications);
    }

    public static DefaultPodSetSpecification create(
            int podCount,
            String podName,
            List<TaskSpecification> taskSpecifications) {

        List<PodSpecification> podSpecifications = new ArrayList<>();
        List<TaskSpecification> indexedTaskSpecifications = new ArrayList<>();

        for (int podIndex = 0; podIndex < podCount; podIndex++) {
            indexedTaskSpecifications.clear();
            for (TaskSpecification taskSpec : taskSpecifications) {
                indexedTaskSpecifications.add(
                        new DefaultTaskSpecification(
                                taskSpec.getName() + "-" + podIndex,
                                taskSpec.getType(),
                                taskSpec.getContainer(),
                                taskSpec.getCommand(),
                                taskSpec.getResources(),
                                taskSpec.getVolumes(),
                                taskSpec.getConfigFiles(),
                                Optional.empty(),
                                taskSpec.getHealthCheck(),
                                podName
                        )
                );
            }
            podSpecifications.add(
                    new DefaultPodSpecification(podName + "-" + podIndex, indexedTaskSpecifications, Optional.empty())
            );
        }
        return new DefaultPodSetSpecification(podName, podSpecifications);
    }

    @JsonCreator
    public static DefaultPodSetSpecification create(
            @JsonProperty("name") String name,
            @JsonProperty("taskgroups") List<PodSpecification> podSpecifications) {
        return new DefaultPodSetSpecification(name, podSpecifications);
    }

    protected DefaultPodSetSpecification(String name, List<PodSpecification> podSpecifications) {
        this.name = name;
        this.podSpecifications = podSpecifications;
    }

    @Override
    public String getName() {
        return name;
    }

    public List<PodSpecification> getPodSpecifications() {
        return podSpecifications;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
