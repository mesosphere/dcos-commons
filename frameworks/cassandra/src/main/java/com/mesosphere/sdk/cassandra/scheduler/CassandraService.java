package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        int configuredSeedCount = Integer.parseInt(System.getenv("SEED_COUNT"));
        int remoteDatacenterCount = Integer.parseInt(System.getenv("REMOTE_DATA_CENTER_COUNT"));
        Collection<String> datacenterNames = IntStream.range(0, remoteDatacenterCount).boxed()
                .map(i -> String.format("dc%d", i))
                .collect(Collectors.toList());

        apiResources.add(
                new SeedsResource(scheduler.getStateStore(), datacenterNames, configuredSeedCount));

        LOGGER.info("Registering remote datacenters: {}", datacenterNames);
        LOGGER.info("Starting API server with additional resources for Cassandra: {}", apiResources);
        apiResources.addAll(additionalResources);

        super.startApiServer(scheduler, apiPort, apiResources);
    }
}
