package com.mesosphere.sdk.reference.scheduler;

import org.apache.mesos.specification.DefaultService;

import java.io.File;

/**
 * Main entry point for the Reference Scheduler.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.exit(1);
        }
        new DefaultService(new File(args[0]));
    }
}
