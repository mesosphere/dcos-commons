package com.mesosphere.sdk.offer.taskdata;

import java.util.Map;
import java.util.TreeMap;

import org.apache.mesos.Protos;

/**
 * Utilities relating to construction of {@link ProcessBuilder}s.
 */
public class ProcessBuilderUtils {
    private ProcessBuilderUtils() {
        // do not instantiate
    }

    /**
     * Returns a Map representation of the provided {@link Protos.Environment}.
     * In the event of duplicate labels, the last duplicate wins.
     */
    public static Map<String, String> toMap(Protos.Environment environment) {
        // sort labels alphabetically for convenience in debugging/logging:
        Map<String, String> map = new TreeMap<>();
        for (Protos.Environment.Variable variable : environment.getVariablesList()) {
            map.put(variable.getName(), variable.getValue());
        }
        return map;
    }

    /**
     * Returns a {@link ProcessBuilder} instance which has been initialized with the provided
     * {@link Protos.CommandInfo}'s command and environment.
     */
    public static ProcessBuilder buildProcess(Protos.CommandInfo cmd) {
        return buildProcess(cmd.getValue(), toMap(cmd.getEnvironment()));
    }

    /**
     * Returns a {@link ProcessBuilder} instance which has been initialized with the provided command and environment.
     */
    private static ProcessBuilder buildProcess(String cmd, Map<String, String> env) {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd).inheritIO();
        builder.environment().putAll(env);
        return builder;
    }
}
