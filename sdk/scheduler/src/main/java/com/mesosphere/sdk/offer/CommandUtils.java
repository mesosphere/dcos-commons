package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;
import com.mesosphere.sdk.specification.CommandSpec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for the construction of {@link org.apache.mesos.Protos.CommandInfo} protobufs
 * from {@link CommandSpec}s.
 */
public class CommandUtils {
    public static List<Protos.CommandInfo.URI> getUris(CommandSpec commandSpec) {
        return commandSpec.getUris().stream()
                .map(uri -> Protos.CommandInfo.URI.newBuilder().setValue(uri.toString()).build())
                .collect(Collectors.toList());
    }

    public static Protos.CommandInfo addEnvVar(
            Protos.CommandInfo command,
            String key,
            String value) {
        Protos.CommandInfo.Builder builder = command.toBuilder();
        builder.getEnvironmentBuilder().addVariablesBuilder().setName(key).setValue(value);
        return builder.build();
    }

    public static String getEnvVar(Protos.CommandInfo command, String key) {
        if (command.hasEnvironment()) {
            for (Protos.Environment.Variable v : command.getEnvironment().getVariablesList()) {
                if (v.getName().equals(key)) {
                    return v.getValue();
                }
            }
        }

        return null;
    }
}
