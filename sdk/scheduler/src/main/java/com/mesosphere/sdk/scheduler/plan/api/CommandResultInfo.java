package com.mesosphere.sdk.scheduler.plan.api;

import com.fasterxml.jackson.annotation.JsonProperty;

class CommandResultInfo {
    private final String msg;

    CommandResultInfo(String msg) {
        this.msg = msg;
    }

    @JsonProperty("message")
    public String getMessage() {
        return msg;
    }
}
