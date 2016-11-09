package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.CommandSpec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by gabriel on 11/9/16.
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
}
