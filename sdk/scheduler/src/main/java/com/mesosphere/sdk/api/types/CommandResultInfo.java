package com.mesosphere.sdk.api.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common utility class for responses to commands
 */
class CommandResultInfo {
    private final String msg;

    CommandResultInfo(String command) {
        this.msg = String.format("Received cmd: %s", command);
    }

    @JsonProperty("message")
    public String getMessage() {
        return msg;
    }
}
