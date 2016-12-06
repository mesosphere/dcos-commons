package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.specification.DefaultService;

import java.io.File;

/**
 * Main.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        new DefaultService(new File(args[0]));
    }
}
