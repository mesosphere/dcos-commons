package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.specification.DefaultService;

import java.io.File;

/**
 * Service for the Elastic framework.
 */
public class ElasticService extends DefaultService {

    ElasticService(File pathToYamlSpecification) throws Exception {
        super(pathToYamlSpecification);
    }

}
