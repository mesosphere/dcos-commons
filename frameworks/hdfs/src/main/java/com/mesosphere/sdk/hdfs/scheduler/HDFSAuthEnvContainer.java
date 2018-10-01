package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

/**
 * This class captures the needed environment variables which are used for the kerberos authentication.
 **/
public class HDFSAuthEnvContainer {
    private static final String AUTH_TO_LOCAL = "AUTH_TO_LOCAL";
    public static final String PRIMARY_ENV_KEY = "TASKCFG_ALL_SECURITY_KERBEROS_PRIMARY";
    public static final String REALM_ENV_KEY = "TASKCFG_ALL_SECURITY_KERBEROS_REALM";
    public static final String FRAMEWORK_USER_ENV_KEY = "TASKCFG_ALL_TASK_USER";
    public static final String DECODED_AUTH_TO_LOCAL = "DECODED_" + AUTH_TO_LOCAL;
    public static final String TASKCFG_ALL_AUTH_TO_LOCAL = TaskEnvRouter.TASKCFG_GLOBAL_ENV_PREFIX + AUTH_TO_LOCAL;
    private static final String[] REQUIRED_ENV_KEYS = {PRIMARY_ENV_KEY, REALM_ENV_KEY, FRAMEWORK_USER_ENV_KEY};

    private final String primary;
    private final String realm;
    private final String frameworkUser;
    private final String envAuthMapping;

    public String getPrimary() {
        return primary;
    }

    public String getEnvAuthMapping() {
        return envAuthMapping;
    }

    public String getRealm() {
        return realm;
    }

    public String getFrameworkUser() {
        return frameworkUser;
    }

    private void checkEnvHasRequiredKeys(Map<String, String> env) {
        ArrayList<String> missingKeys = new ArrayList<>();
        for (String envKey : REQUIRED_ENV_KEYS) {
            if (env.containsKey(envKey)) {
                continue;
            }
            missingKeys.add(envKey);
        }
        if (!missingKeys.isEmpty()) {
            String msg = String.format("The following environment keys are missing %s", String.join(", ", missingKeys));
            throw new RuntimeException(msg);
        }
    }

    public HDFSAuthEnvContainer(Map<String, String> env) {
        checkEnvHasRequiredKeys(env);
        this.envAuthMapping = getAuthMappingFromEnv(env);
        this.primary = env.get(PRIMARY_ENV_KEY);
        this.frameworkUser = env.get(FRAMEWORK_USER_ENV_KEY);
        this.realm = env.get(REALM_ENV_KEY);
    }

    private String getAuthMappingFromEnv(Map<String, String> env) {
        if (!env.containsKey(TASKCFG_ALL_AUTH_TO_LOCAL)) {
            return "";
        }
        Base64.Decoder decoder = Base64.getDecoder();
        String base64Mappings = env.get(TASKCFG_ALL_AUTH_TO_LOCAL);
        byte[] hdfsUserAuthMappingsBytes = decoder.decode(base64Mappings);
        return new String(hdfsUserAuthMappingsBytes, StandardCharsets.UTF_8);
    }
}
