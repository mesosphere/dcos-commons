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

    String getType();

    /**
     * Returns the Mesos {@code CommandInfo} to be used for the starting task.
     */
    Protos.CommandInfo getCommand();

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
     * Returns the placement constraint for this task type. This can be anything from "colocate with
     * another type", "ensure instances are only launched on nodes with attribute X", etc. This may
     * return an empty {@code Optional} if no specific placement rules are applicable.
     *
     * See the documentation for {@link PlacementRuleGenerator} and {@link PlacementRule} for more
     * information.
     */
    Optional<PlacementRuleGenerator> getPlacement();
}
