package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * This implementation of the TaskSpecification is for test purposes.  It allows what would otherwise be bad practices
 * like changing the ResourceSpecifications encapsulated by the TaskSpecification after construction.
 */
public class TestTaskSpecification implements TaskSpecification {
    private final String name;
    private final Protos.CommandInfo command;
    private final Collection<VolumeSpecification> volumes;
    private Collection<ResourceSpecification> resources;
    private Collection<ConfigFileSpecification> configs;
    private final Optional<PlacementRuleGenerator> placement;
    private Optional<Protos.HealthCheck> healthCheck;

    public TestTaskSpecification(TaskSpecification taskSpecification) {
        this.name = taskSpecification.getName();
        this.command = taskSpecification.getCommand();
        this.resources = taskSpecification.getResources();
        this.volumes = taskSpecification.getVolumes();
        this.configs = taskSpecification.getConfigFiles();
        this.placement = taskSpecification.getPlacement();
        this.healthCheck = taskSpecification.getHealthCheck();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Protos.CommandInfo getCommand() {
        return command;
    }

    @Override
    public Optional<Protos.HealthCheck> getHealthCheck() { return healthCheck; }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return resources;
    }

    @Override
    public Collection<VolumeSpecification> getVolumes() {
        return volumes;
    }

    @Override
    public Collection<ConfigFileSpecification> getConfigFiles() {
        return configs;
    }

    @Override
    public Optional<PlacementRuleGenerator> getPlacement() {
        return placement;
    }

    public TestTaskSpecification addResource(ResourceSpecification resourceSpecification) {
        resources = new ArrayList<>(resources); // ensure that we can add()
        resources.add(resourceSpecification);
        return this;
    }

    public TestTaskSpecification addConfigFile(ConfigFileSpecification configFileSpecification) {
        configs = new ArrayList<>(configs); // ensure that we can add()
        configs.add(configFileSpecification);
        return this;
    }
}
