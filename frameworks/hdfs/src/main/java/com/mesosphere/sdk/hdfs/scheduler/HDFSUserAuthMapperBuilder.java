package com.mesosphere.sdk.hdfs.scheduler;

import java.util.ArrayList;
import java.util.Map;

/**
 * This class helps to generate user auth mappings for the HDFS setup with kerberos.
 */
public class HDFSUserAuthMapperBuilder {
    private static final String authToLocalDefaultTemplate = "RULE:[2:$1/$2@$0](%s/%s.%s@%s)s/.*/%s/";
    private final ArrayList<String> authMappings = new ArrayList<>();
    private final String frameworkHost;
    private final HDFSAuthEnvContainer envContainer;

    public HDFSUserAuthMapperBuilder(Map<String, String> env, String frameworkHost) {
        this.frameworkHost = frameworkHost;
        this.envContainer = new HDFSAuthEnvContainer(env);
    }

    public HDFSUserAuthMapperBuilder addUserAuthMappingFromEnv() {
        authMappings.add(envContainer.getEnvAuthMapping());
        return this;
    }

    public HDFSUserAuthMapperBuilder addDefaultUserAuthMapping(String hostPrefix, String hostPostfix, int nodeCount) {
        for (int i = 0; i < nodeCount; i++) {
            String taskName = String.format("%s-%s-%s", hostPrefix, i, hostPostfix);
            String authMapping = String.format(authToLocalDefaultTemplate, envContainer.getPrimary(), taskName,
                    frameworkHost, envContainer.getRealm(), envContainer.getFrameworkUser());
            authMappings.add(authMapping);
        }
        return this;
    }

    public String build() {
        authMappings.removeIf(String::isEmpty);
         return String.join("\n", authMappings);
    }

}
