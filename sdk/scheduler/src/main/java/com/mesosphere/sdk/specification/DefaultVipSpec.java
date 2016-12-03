package com.mesosphere.sdk.specification;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.mesosphere.sdk.specification.validation.ValidationUtils;

/**
 * Default implementation of {@link VipSpec}.
 */
public class DefaultVipSpec implements VipSpec {
    @NotNull
    @Min(0)
    private Integer applicationPort;
    @NotNull
    @Size(min = 1, message = "prefix cannot be empty.")
    private String vipName;
    @NotNull
    @Min(0)
    private Integer vipPort;

    private DefaultVipSpec(Builder builder) {
        applicationPort = builder.applicationPort;
        vipName = builder.vipName;
        vipPort = builder.vipPort;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultVipSpec copy) {
        Builder builder = new Builder();
        builder.applicationPort = copy.applicationPort;
        builder.vipName = copy.vipName;
        builder.vipPort = copy.vipPort;
        return builder;
    }

    @Override
    public int getApplicationPort() {
        return applicationPort;
    }

    @Override
    public String getVipName() {
        return vipName;
    }

    @Override
    public int getVipPort() {
        return vipPort;
    }


    /**
     * {@code DefaultVipSpec} builder static inner class.
     */
    public static final class Builder {
        private int applicationPort;
        private String vipName;
        private int vipPort;

        private Builder() {
        }

        /**
         * Sets the {@code applicationPort} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param applicationPort the {@code applicationPort} to set
         * @return a reference to this Builder
         */
        public Builder applicationPort(int applicationPort) {
            this.applicationPort = applicationPort;
            return this;
        }

        /**
         * Sets the {@code vipName} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param vipName the {@code vipName} to set
         * @return a reference to this Builder
         */
        public Builder vipName(String vipName) {
            this.vipName = vipName;
            return this;
        }

        /**
         * Sets the {@code vipPort} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param vipPort the {@code vipPort} to set
         * @return a reference to this Builder
         */
        public Builder vipPort(int vipPort) {
            this.vipPort = vipPort;
            return this;
        }

        /**
         * Returns a {@code DefaultVipSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultVipSpec} built with parameters of this {@code DefaultVipSpec.Builder}
         */
        public DefaultVipSpec build() {
            DefaultVipSpec defaultVipSpec = new DefaultVipSpec(this);
            ValidationUtils.validate(defaultVipSpec);
            return defaultVipSpec;
        }
    }
}
