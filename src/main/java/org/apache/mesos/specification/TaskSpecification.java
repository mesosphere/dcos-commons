package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.Collection;
import java.util.Optional;

/**
 * A TaskSpecification is a simplified description of a Mesos Task.
 */
public interface TaskSpecification {
    /**
     * Returns the name of this task. E.g. "index-3" for the fourth index node or "data-0" for the
     * first data node.
     */
    String getName();

    /**
     * Returns the Mesos {@link Protos.CommandInfo} to be used for the starting task.
     */
    Protos.CommandInfo getCommand();

    /**
     * Returns the health check operation to be performed against this task while it's running, or
     * an empty {@link Optional} if no additional health checks should be performed beyond Mesos
     * task status.
     */
    Optional<Protos.HealthCheck> getHealthCheck();

    /**
     * Returns the container resources required by tasks of this type, or an empty list if no
     * container resources are necessary. In practice you probably want something here.
     *
     * See the documentation for {@link ResourceSpecification} for more information.
     */
    Collection<ResourceSpecification> getResources();

    /**
     * Returns the persistent volumes required for this task, or an empty list if no persistent
     * volumes are necessary, e.g. when not using persistent volumes.
     *
     * See the documentation for {@link VolumeSpecification} for more information.
     */
    Collection<VolumeSpecification> getVolumes();

    /**
     * Returns the Configuration Files to be written for this task, or an empty list if no custom
     * configurations should be written.
     *
     * Note: There is a limit of 100KB (102,400B) across all template data for a given task. If you
     * need to exceed this limit, consider using resource files.
     */
    Collection<ConfigFileSpecification> getConfigFiles();

    /**
     * Returns the placement constraint for this task. This can be anything from "colocate with
     * another task", "ensure instances are only launched on nodes with attribute X", etc. This may
     * return an empty {@code Optional} if no specific placement rules are applicable.
     *
     * See the documentation for {@link PlacementRuleGenerator} and {@link PlacementRule} for more
     * information.
     */
    Optional<PlacementRuleGenerator> getPlacement();
}
