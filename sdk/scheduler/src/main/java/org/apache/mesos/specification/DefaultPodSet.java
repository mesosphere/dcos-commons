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
 * This class provides a default implementation of the PodSet interface.
 */
public class DefaultPodSet implements PodSet {
    private final String name;
    private final List<Pod> pods;

    public static DefaultPodSet create(
            int podCount,
            String name,
            Optional<PlacementRuleGenerator> placement,
            List<TaskSpecification> givenTaskSpecifications) {

        List<Pod> pods = new ArrayList<>();
        List<TaskSpecification> indexedTaskSpecification = new ArrayList<>();

        for (int podIndex = 0; podIndex < podCount; podIndex++) {
            indexedTaskSpecification.clear();
            for (TaskSpecification taskSpecification : givenTaskSpecifications) {
                indexedTaskSpecification.add(
                        new DefaultTaskSpecification(
                                taskSpecification.getName() + "-" + podIndex,
                                taskSpecification.getType(),
                                taskSpecification.getContainer(),
                                taskSpecification.getCommand(),
                                taskSpecification.getResources(),
                                taskSpecification.getVolumes(),
                                taskSpecification.getConfigFiles(),
                                placement,
                                taskSpecification.getHealthCheck()
                        )
                );
            }
            pods.add(
                    new DefaultPod(name + "-" + podIndex, indexedTaskSpecification, placement)
            );
        }

        return new DefaultPodSet(name, pods);
    }

    //public static DefaultPodSet create(
    //        int count,
    //        String name,
    //        String type,
    //        Protos.CommandInfo command,
    //        Collection<ResourceSpecification> resources,
    //        Collection<VolumeSpecification> volumes) {

    //    return create(
    //            count,
    //            name,
    //            type,
    //            Optional.empty(),
    //            Optional.of(command),
    //            resources,
    //            volumes,
    //            new ArrayList<>() /* configs */,
    //            Optional.empty() /* placement */,
    //            Optional.empty() /* healthcheck */);
    //}

    //public static DefaultPodSet create(
    //        int count,
    //        String name,
    //        String type,
    //        Protos.CommandInfo command,
    //        Collection<ResourceSpecification> resources,
    //        Collection<VolumeSpecification> volumes,
    //        Collection<ConfigFileSpecification> configs,
    //        Optional<PlacementRuleGenerator> placementOptional,
    //        Optional<Protos.HealthCheck> healthCheck) {

    //    return create(
    //            count,
    //            name,
    //            type,
    //            Optional.empty(),
    //            Optional.of(command),
    //            resources,
    //            volumes,
    //            configs,
    //            placementOptional,
    //            healthCheck);
    //}

    //public static DefaultPodSet create(
    //        int count,
    //        String name,
    //        String type,
    //        Protos.ContainerInfo container,
    //        Collection<ResourceSpecification> resources,
    //        Collection<VolumeSpecification> volumes,
    //        Collection<ConfigFileSpecification> configs,
    //        Optional<PlacementRuleGenerator> placementOptional,
    //        Optional<Protos.HealthCheck> healthCheck) {

    //    return create(
    //            count,
    //            name,
    //            type,
    //            Optional.of(container),
    //            Optional.empty(),
    //            resources,
    //            volumes,
    //            configs,
    //            placementOptional,
    //            healthCheck);
    //}

    //public static DefaultPodSet create(
    //        int count,
    //        String name,
    //        String type,
    //        Protos.ContainerInfo container,
    //        Protos.CommandInfo command,
    //        Collection<ResourceSpecification> resources,
    //        Collection<VolumeSpecification> volumes,
    //        Collection<ConfigFileSpecification> configs,
    //        Optional<PlacementRuleGenerator> placementOptional,
    //        Optional<Protos.HealthCheck> healthCheck) {

    //    return create(
    //            count,
    //            name,
    //            type,
    //            Optional.of(container),
    //            Optional.of(command),
    //            resources,
    //            volumes,
    //            configs,
    //            placementOptional,
    //            healthCheck);
    //}

    //private static DefaultPodSet create(
    //        int count,
    //        String name,
    //        String type,
    //        Optional<Protos.ContainerInfo> container,
    //        Optional<Protos.CommandInfo> command,
    //        Collection<ResourceSpecification> resources,
    //        Collection<VolumeSpecification> volumes,
    //        Collection<ConfigFileSpecification> configs,
    //        Optional<PlacementRuleGenerator> placementOptional,
    //        Optional<Protos.HealthCheck> healthCheck) {

    //    List<TaskSpecification> taskSpecifications = new ArrayList<>();
    //    for (int i = 0; i < count; i++) {
    //        taskSpecifications.add(new DefaultTaskSpecification(
    //                name + "-" + i,
    //                type,
    //                container,
    //                command,
    //                resources,
    //                volumes,
    //                configs,
    //                placementOptional,
    //                healthCheck));
    //    }

    //    return create(name, taskSpecifications);
    //}

    @JsonCreator
    public static DefaultPodSet create(
            @JsonProperty("name") String name,
            @JsonProperty("taskgroups") List<Pod> pods) {
        return new DefaultPodSet(name, pods);
    }

    protected DefaultPodSet(String name, List<Pod> pods) {
        this.name = name;
        this.pods = pods;
    }

    @Override
    public String getName() {
        return name;
    }

    public List<Pod> getPods() {
        return pods;
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
