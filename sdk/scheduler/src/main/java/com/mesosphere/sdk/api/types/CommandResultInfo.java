package com.mesosphere.sdk.api.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common utility class for responses to commands.
 */
public class CommandResultInfo {
    private final String msg;

    public CommandResultInfo(String command) {
        this.msg = String.format("Received cmd: %s", command);
    }

    @JsonProperty("message")
    public String getMessage() {
        return msg;
    }
}
