package com.mesosphere.sdk.cassandra.scheduler;

import java.io.File;

/**
 * Cassandra Service.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        new CassandraService(new File(args[0])).run();
    }
}
