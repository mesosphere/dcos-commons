package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.specification.CommandSpec;
import org.apache.mesos.Protos;

import java.util.Map;

/**
 * This class provides utility methods for the construction of {@link org.apache.mesos.Protos.CommandInfo} protobufs
 * from {@link CommandSpec}s.
 */
public class CommandUtils {

    public static Protos.CommandInfo setEnvVar(
            Protos.CommandInfo.Builder builder,
            String key,
            String value) {
        Map<String, String> envMap = CommonTaskUtils.fromEnvironmentToMap(builder.getEnvironment());
        envMap.put(key, value);
        builder.setEnvironment(CommonTaskUtils.fromMapToEnvironment(envMap));
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
