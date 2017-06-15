package com.mesosphere.sdk.cockroachdb.scheduler;

import com.mesosphere.sdk.specification.*;

import java.io.File;

/**
 * CockroachDB service.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        new CockroachdbService(new File(args[0])).run();
    }
}
