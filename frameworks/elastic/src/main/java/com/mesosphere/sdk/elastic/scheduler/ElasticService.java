package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.specification.DefaultService;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Service for the Elastic framework.
 */
public class ElasticService extends DefaultService {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ElasticService.class);

    ElasticService(File pathToYamlSpecification) throws Exception {
        super(pathToYamlSpecification);
    }

    protected static Protos.FrameworkInfo.Builder getFrameworkInfoBuilder(String serviceName) {
        String webuiUrl = String.format("http://kibana-0-server.%s.mesos:%s", serviceName,
                System.getenv("KIBANA_PORT"));
        LOGGER.info("Setting web UI URL: " + webuiUrl);

        return Protos.FrameworkInfo.newBuilder()
                .setName(serviceName)
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setUser(USER)
                .setWebuiUrl(webuiUrl)
                .setCheckpoint(true);
    }


}
