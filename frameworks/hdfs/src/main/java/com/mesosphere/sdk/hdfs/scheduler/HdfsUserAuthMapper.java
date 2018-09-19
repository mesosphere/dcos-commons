package com.mesosphere.sdk.hdfs.scheduler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

public class HdfsUserAuthMapper {

    private String primary;
    private String frameworkHost;
    private String realm;
    private String frameworkUser;
    private final String AUTH_TO_LOCAL_DEFAULT_TEMPLATE = "RULE:[2:$1/$2@$0](%s/%s.%s@%s)s/.*/%s/";
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

    public void addDefaultUserAuthMapping(String hostPrefix, int nodeCount) {
        for (int i = 0; i < nodeCount; i++) {
            String taskName = String.format("%s-%s-node", hostPrefix, i);
            String authMapping = String.format(AUTH_TO_LOCAL_DEFAULT_TEMPLATE, primary, taskName,
                    frameworkHost, realm, frameworkUser);
            authMappings.add(authMapping);

        }
    }

    public String getUserAuthMappingString() {
        return String.join("/n", authMappings);
    }

}
