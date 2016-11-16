package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.specification.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;


/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);


    private static final int API_PORT = Integer.parseInt(System.getenv("PORT0"));

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting Elastic scheduler with args: " + Arrays.asList(args));
        Elastic elastic = new Elastic();
        Service service = new ElasticService(API_PORT, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
        service.register(elastic.getServiceSpecification());
    }

}
