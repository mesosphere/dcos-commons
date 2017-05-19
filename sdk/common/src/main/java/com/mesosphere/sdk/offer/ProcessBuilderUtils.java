package com.mesosphere.sdk.offer;

import java.util.Map;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.offer.taskdata.EnvUtils;

/**
 * Utilities relating to construction of {@link ProcessBuilder}s.
 */
public class ProcessBuilderUtils {
    private ProcessBuilderUtils() {
        // do not instantiate
    }

    /**
     * Returns a {@link ProcessBuilder} instance which has been initialized with the provided
     * {@link Protos.CommandInfo}'s command and environment.
     */
    public static ProcessBuilder buildProcess(Protos.CommandInfo cmd) {
        return buildProcess(cmd.getValue(), EnvUtils.toMap(cmd.getEnvironment()));
    }

    /**
     * Returns a {@link ProcessBuilder} instance which has been initialized with the provided command and environment.
     */
    public static ProcessBuilder buildProcess(String cmd, Map<String, String> env) {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd).inheritIO();
        builder.environment().putAll(env);
        return builder;
    }
}
