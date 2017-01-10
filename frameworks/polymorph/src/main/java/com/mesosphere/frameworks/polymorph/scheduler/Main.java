package com.mesosphere.frameworks.polymorph.scheduler;

import com.mesosphere.sdk.specification.DefaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Polymorph Framework.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            LOGGER.error(
                    "No service specification provided. Please provide a YAML file containing service specification.");
        }
        String pathToServiceSpecification = args[0];
        LOGGER.info("Reading service specification from: {}", pathToServiceSpecification);
        File serviceSpeficiationFile = new File(args[0]);
        if (!serviceSpeficiationFile.exists()) {
            LOGGER.error("Service specification file doesn't exist at location: {}", pathToServiceSpecification);
        }
        new DefaultService(serviceSpeficiationFile);
    }
}
