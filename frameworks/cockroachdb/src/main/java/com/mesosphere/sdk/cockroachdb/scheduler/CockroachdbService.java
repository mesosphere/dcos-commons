package com.mesosphere.sdk.cockroachdb.scheduler;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
//import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;

/**
 * CockroachDB Service.
 */
public class CockroachdbService extends DefaultService {
    protected static final Logger LOGGER = LoggerFactory.getLogger(CockroachdbService.class);

    public CockroachdbService(File pathToYamlSpecification) throws Exception {
        super(createSchedulerBuilder(pathToYamlSpecification));
    }

    private static DefaultScheduler.Builder createSchedulerBuilder(File pathToYamlSpecification)
            throws Exception {
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(pathToYamlSpecification).build();
        DefaultScheduler.Builder schedulerBuilder =
                DefaultScheduler.newBuilder(
                        DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerFlags).build(),
                        schedulerFlags)
                        .setPlansFrom(rawServiceSpec)
                        .setRecoveryManagerFactory(new CockroachdbRecoveryPlanOverriderFactory());
        return schedulerBuilder;
    }
}
