package com.mesosphere.sdk.hdfs.scheduler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

/**
 * This class helps to generate user auth mappings for the HDFS setup with kerberos.
 */
public class HdfsUserAuthMapper {

    private final String primary;
    private final String frameworkHost;
    private final String realm;
    private final String frameworkUser;
    private static final String authToLocalDefaultTemplate = "RULE:[2:$1/$2@$0](%s/%s.%s@%s)s/.*/%s/";
    private final ArrayList<String> authMappings = new ArrayList<>();

    public HdfsUserAuthMapper(String primary, String frameworkHost, String realm, String frameworkUser) {
        this.primary = primary;
        this.frameworkHost = frameworkHost;
        this.realm = realm;
        this.frameworkUser = frameworkUser;
    }

    public void addUserAuthMappingFromEnv(Map<String, String> env, String envVarKeyName) {
        Base64.Decoder decoder = Base64.getDecoder();
        String base64Mappings = env.get(envVarKeyName);
        byte[] hdfsUserAuthMappingsBytes = decoder.decode(base64Mappings);
        authMappings.add(new String(hdfsUserAuthMappingsBytes, StandardCharsets.UTF_8));
    }

    public void addDefaultUserAuthMapping(String hostPrefix, String hostPostfix, int nodeCount) {
        for (int i = 0; i < nodeCount; i++) {
            String taskName = String.format("%s-%s-%s", hostPrefix, i, hostPostfix);
            String authMapping = String.format(authToLocalDefaultTemplate, primary, taskName,
                    frameworkHost, realm, frameworkUser);
            authMappings.add(authMapping);
        }
    }

    public String getUserAuthMappingString() {
        return String.join("\n", authMappings);
    }

}
