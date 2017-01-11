package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.specification.DefaultService;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka service.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            new DefaultService(new File(args[0]));
        } else {
            LOGGER.error("Missing file argument");
            System.exit(1);
        }
    }
}
