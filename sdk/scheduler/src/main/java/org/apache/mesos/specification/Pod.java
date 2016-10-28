package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.List;
import java.util.Optional;

/**
 * A {@code Pod} groups one or more {@link TaskSpecification}s so that they can run in the same container.
 */
public interface Pod {
    /**
     * The name of the Pod.
     */
    String getName();

    /**
     * The list of {@link TaskSpecification} belonging to the Pod.
     */
    List<TaskSpecification> getTaskSpecifications();

    /**
     * Returns the placement constraint for this pod. This can be anything from "colocate with
     * another pod which has a desired task", "ensure instances are only launched on nodes with attribute X", etc.
     * This may return an empty {@code Optional} if no specific placement rules are applicable.
     *
     * See the documentation for {@link PlacementRuleGenerator} and
     * {@link org.apache.mesos.offer.constrain.PlacementRule} for more information.
     */
    @JsonProperty("placement")
    Optional<PlacementRuleGenerator> getPlacement();
}
