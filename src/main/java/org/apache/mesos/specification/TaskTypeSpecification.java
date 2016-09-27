package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.Collection;
import java.util.Optional;

/**
 * A TaskTypeSpecification describes a Type of Task and how many should be launched.
 */
public interface TaskTypeSpecification {

    /**
     * Returns the number of tasks of this type to be deployed.
     */
    int getCount();

    /**
     * Returns the name of this task type. Eg "index" for an index node type or "data" for a data
     * node type.
     */
    String getName();

    /**
     * Returns the Mesos {@code CommandInfo} to be used for starting the task with index {@code id}.
     * The {@code id} parameter allows slightly different commands for different tasks of the same
     * type.
     */
    Protos.CommandInfo getCommand(int id);

    /**
     * Returns the contanier resources required by tasks of this type, or an empty list if no
     * container resources are necessary. But you probably want something here.
     *
     * See the documentation for {@link ResourceSpecification} for more information.
     */
    Collection<ResourceSpecification> getResources();

    /**
     * Returns the persistent volumes required by tasks of this type, or an empty list if no
     * persistent volumes are necessary, eg when not using persistent volumes.
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
