package org.apache.mesos.specification;

import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.List;
import java.util.Optional;

/**
 * This class provides a default implementation of the {@link Pod} interface.
 */
public class DefaultPod implements Pod {
    private String name;
    private List<TaskSpecification> taskSpecifications;
    private Optional<PlacementRuleGenerator> placementRuleGeneratorOptional;

    public DefaultPod(
            String name,
            List<TaskSpecification> taskSpecifications,
            Optional<PlacementRuleGenerator> placementRuleGeneratorOptional) {
        this.name = name;
        this.taskSpecifications = taskSpecifications;
        this.placementRuleGeneratorOptional = placementRuleGeneratorOptional;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<TaskSpecification> getTaskSpecifications() {
        return taskSpecifications;
    }

    @Override
    public Optional<PlacementRuleGenerator> getPlacement() {
        return placementRuleGeneratorOptional;
    }
}
