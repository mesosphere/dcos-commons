package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.specification.DefaultService;

import java.io.File;

/**
 * Cassandra Service.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        new DefaultService(new File(args[0]));
    }
}
