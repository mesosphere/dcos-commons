package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Raw YAML secret.
 */
public class RawSecret {

    private final String secretPath;
    private final String envKey;
    private final String filePath;

    private RawSecret(
            @JsonProperty("secret") String secretPath,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("file") String filePath) {
        this.secretPath = secretPath;
        this.envKey = envKey;
        this.filePath = filePath;
    }

    public String getSecretPath() {
        return secretPath;
    }

    public String getEnvKey() {
        return envKey;
    }

    public String getFilePath() { return filePath; }
}
