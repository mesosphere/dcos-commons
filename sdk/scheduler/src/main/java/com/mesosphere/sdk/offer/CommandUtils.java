package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.specification.CommandSpec;
import org.apache.mesos.Protos;

import java.util.Map;
import java.util.Optional;

/**
 * This class provides utility methods for the construction of {@link org.apache.mesos.Protos.CommandInfo} protobufs
 * from {@link CommandSpec}s.
 */
public class CommandUtils {

    /**
     * Adds or updates the provided environment variable entry in the provided command builder.
     */
    public static void setEnvVar(
            Protos.CommandInfo.Builder builder,
            String key,
            String value) {
        Map<String, String> envMap = EnvUtils.fromEnvironmentToMap(builder.getEnvironment());
        envMap.put(key, value);
        builder.setEnvironment(EnvUtils.fromMapToEnvironment(envMap));
    }

    /**
     * Returns the value of the provided environment variable, or an empty {@link Optional} if no matching environment
     * variable was found.
     */
    public static Optional<String> getEnvVar(Protos.CommandInfo command, String key) {
        if (command.hasEnvironment()) {
            for (Protos.Environment.Variable v : command.getEnvironment().getVariablesList()) {
                if (v.getName().equals(key)) {
                    return Optional.of(v.getValue());
                }
            }
        }

        return Optional.empty();
    }
}
