package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRule;
import org.apache.mesos.specification.ConfigFileSpecification;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.ResourceSpecification;
import org.apache.mesos.specification.VolumeSpecification;

import java.util.Collection;
import java.util.Optional;

class ElasticsearchTaskSpecification extends DefaultTaskSpecification {
    ElasticsearchTaskSpecification(String name,
                                   String type,
                                   Protos.CommandInfo commandInfo,
                                   Collection<ResourceSpecification> resourceSpecs,
                                   Collection<VolumeSpecification> volumeSpecs,
                                   Collection<ConfigFileSpecification> configFileSpecs,
                                   Optional<PlacementRule> placementRule,
                                   Optional<Protos.HealthCheck> healthCheck) {

        super(name, type, commandInfo, resourceSpecs, volumeSpecs, configFileSpecs, placementRule, healthCheck);
    }
}
