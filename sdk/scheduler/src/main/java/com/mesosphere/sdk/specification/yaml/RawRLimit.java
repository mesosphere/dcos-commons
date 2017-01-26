package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML individual rlimit specification.
 */
public class RawRLimit {
    private final Long soft;
    private final Long hard;

    @JsonCreator
    public RawRLimit(@JsonProperty("soft") Long soft, @JsonProperty("hard") Long hard) {
        this.soft = soft;
        this.hard = hard;
    }

    private RawRLimit(Builder builder) {
        this(builder.soft, builder.hard);
    }

    public Long getSoft() {
        return soft;
    }

    public Long getHard() {
        return hard;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link RawRLimit} builder.
     */
    public static final class Builder {
        private Long soft;
        private Long hard;

        private Builder() {
        }

        public Builder soft(Long soft) {
            this.soft = soft;
            return this;
        }

        public Builder hard(Long hard) {
            this.hard = hard;
            return this;
        }

        public RawRLimit build() {
            return new RawRLimit(this);
        }
    }
}
