package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.CommandSpec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for the construction of {@link org.apache.mesos.Protos.CommandInfo} protobufs
 * from {@link CommandSpec}s.
 */
public class CommandUtils {
    public static Protos.CommandInfo getCommandInfo(CommandSpec commandSpec) {
        return Protos.CommandInfo.newBuilder()
                .setValue(commandSpec.getValue())
                .setEnvironment(TaskUtils.fromMapToEnvironment(commandSpec.getEnvironment()))
                .addAllUris(CommandUtils.getUris(commandSpec))
                .build();
    }

    public static List<Protos.CommandInfo.URI> getUris(CommandSpec commandSpec) {
        return commandSpec.getUris().stream()
                .map(uri -> Protos.CommandInfo.URI.newBuilder().setValue(uri.toString()).build())
                .collect(Collectors.toList());
    }

    public static Protos.CommandInfo addEnvVar(
            Protos.CommandInfo command,
            String key,
            String value) {

        Protos.Environment.Builder envBuilder;
        if (command.hasEnvironment()) {
            envBuilder = command.getEnvironment().toBuilder();
        } else {
            envBuilder = Protos.Environment.newBuilder();
        }

        return command.toBuilder()
                .setEnvironment(envBuilder
                        .addVariables(Protos.Environment.Variable.newBuilder()
                                .setName(key)
                                .setValue(value)
                                .build()))
                .build();
    }
}
