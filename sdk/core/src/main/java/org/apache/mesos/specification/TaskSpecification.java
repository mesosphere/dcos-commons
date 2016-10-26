package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Collection;
import java.util.Optional;

/**
 * A TaskSpecification is a simplified description of a Mesos Task.
 */
@JsonDeserialize(as = DefaultTaskSpecification.class)
public interface TaskSpecification {
    /**
     * Returns the name of this task. E.g. "index-3" for the fourth index node or "data-0" for the
     * first data node.
     */
    @JsonProperty("name")
    String getName();

    /**
     * The type of a TaskSpecification should be the name of the {@link TaskSet} to which it belongs.
     *
     * @return the type of the TaskSpecification
     */
    @JsonProperty("type")
    String getType();

    /**
     * Returns the Mesos {@code ContainerInfo} to be used for the starting task.
     */
    @JsonProperty("container")
    Optional<Protos.ContainerInfo> getContainer();

    /**
     * Returns the Mesos {@code CommandInfo} to be used for the starting task.
     */
    @JsonProperty("command")
    Optional<Protos.CommandInfo> getCommand();

    /**
     * Returns the health check operation to be performed against this task while it's running, or
     * an empty {@link Optional} if no additional health checks should be performed beyond Mesos
     * task status.
     */
    @JsonProperty("health_check")
    Optional<Protos.HealthCheck> getHealthCheck();

    /**
     * Returns the container resources required by tasks of this type, or an empty list if no
     * container resources are necessary. In practice you probably want something here.
     *
     * See the documentation for {@link ResourceSpecification} for more information.
     */
    @JsonProperty("resources")
    Collection<ResourceSpecification> getResources();

    /**
     * Returns the persistent volumes required for this task, or an empty list if no persistent
     * volumes are necessary, e.g. when not using persistent volumes.
     *
     * See the documentation for {@link VolumeSpecification} for more information.
     */
    @JsonProperty("volumes")
    Collection<VolumeSpecification> getVolumes();

    /**
     * Returns the Configuration Files to be written for this task, or an empty list if no custom
     * configurations should be written.
     *
     * Note: There is a 512KB limit for all template data for a given task, to keep the resulting
     * Mesos TaskInfo message from growing too large. If you need to exceed this limit, consider
     * using resource files.
     */
    @JsonProperty("config_files")
    Collection<ConfigFileSpecification> getConfigFiles();

    /**
     * Returns the placement constraint for this task. This can be anything from "colocate with
     * another task", "ensure instances are only launched on nodes with attribute X", etc. This may
     * return an empty {@code Optional} if no specific placement rules are applicable.
     *
     * See the documentation for {@link PlacementRuleGenerator} and {@link PlacementRule} for more
     * information.
     */
    @JsonProperty("placement")
    Optional<PlacementRuleGenerator> getPlacement();
}
