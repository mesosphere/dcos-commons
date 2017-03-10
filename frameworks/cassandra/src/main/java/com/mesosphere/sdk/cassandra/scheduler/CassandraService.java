package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Cassandra Service.
 */
public class CassandraService extends DefaultService {
    protected static final Logger LOGGER = LoggerFactory.getLogger(CassandraService.class);

    public CassandraService(File pathToYamlSpecification) throws Exception {
        super(pathToYamlSpecification);
    }

    @Override
    protected void startApiServer(DefaultScheduler scheduler, int apiPort, Collection<Object> additionalResources) {
        final Collection<Object> apiResources = new ArrayList<>();
        Collection<String> configuredSeeds = Arrays.asList(System.getenv("LOCAL_SEEDS").split(","));

        apiResources.add(new SeedsResource(scheduler.getStateStore(), configuredSeeds));
        LOGGER.info("Starting API server with additional resources for Cassandra: {}", apiResources);
        apiResources.addAll(additionalResources);

        super.startApiServer(scheduler, apiPort, apiResources);
    }
}
