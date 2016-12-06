package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.specification.DefaultService;

import java.io.File;


/**
 * Main entry point for the Scheduler.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        new DefaultService(new File(args[0]));
    }
}
