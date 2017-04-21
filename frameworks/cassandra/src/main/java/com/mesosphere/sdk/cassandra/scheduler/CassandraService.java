package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.apache.commons.lang3.StringUtils;
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
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification);
        DefaultScheduler.Builder schedulerBuilder =
                DefaultScheduler.newBuilder(
                        YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, schedulerFlags),
                        schedulerFlags)
                .setPlansFrom(rawServiceSpec)
                .setCustomResources(getResources());
        initService(schedulerBuilder);
    }

    private Collection<Object> getResources() {
        final Collection<Object> apiResources = new ArrayList<>();
        Collection<String> configuredSeeds = new ArrayList<>(
                Arrays.asList(System.getenv("TASKCFG_ALL_LOCAL_SEEDS").split(",")));
        String remoteSeeds = System.getenv("TASKCFG_ALL_REMOTE_SEEDS");

        if (!StringUtils.isEmpty(remoteSeeds)) {
            configuredSeeds.addAll(Arrays.asList(remoteSeeds.split(",")));
        }
        apiResources.add(new SeedsResource(configuredSeeds));

        return apiResources;
    }
}
