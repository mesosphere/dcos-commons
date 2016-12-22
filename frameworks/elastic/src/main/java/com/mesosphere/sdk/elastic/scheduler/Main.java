package com.mesosphere.sdk.elastic.scheduler;

import java.io.File;


/**
 * Main entry point for the Scheduler.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        ElasticService elasticService = new ElasticService(new File(args[0]));
        elasticService.init();
        elasticService.register();
    }
}
