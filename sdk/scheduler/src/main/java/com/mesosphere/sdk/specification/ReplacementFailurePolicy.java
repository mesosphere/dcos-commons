package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Destructive Replacement Failure Policy configuration.
 */
public final class ReplacementFailurePolicy {

  /**
   * Default time to wait between destructive task recoveries (avoid quickly making things worse).
   * <p>
   * Default: 10 minutes
   */
  public static final Integer DEFAULT_DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SECS = 10 * 60;

  /**
   * Default time to wait before declaring a task as permanently failed.
   * <p>
   * Default: 20 minutes
   */
  private static final Integer DEFAULT_PERMANENT_FAILURE_DELAY_SECS = 20 * 60;

  private final Integer permanentFailureTimeoutSecs;

  private final Integer minReplaceDelaySecs;

  private ReplacementFailurePolicy(
      @JsonProperty("permanent-failure-timeout-secs") Integer permanentFailureTimeoutSecs,
      @JsonProperty("min-replace-delay-secs") Integer minReplaceDelaySecs)
  {
    this.permanentFailureTimeoutSecs = permanentFailureTimeoutSecs;
    this.minReplaceDelaySecs = minReplaceDelaySecs;
  }

  private ReplacementFailurePolicy(Builder builder) {
    this(builder.permanentFailureTimoutSecs, builder.minReplaceDelaySecs);
    ValidationUtils.nonNegative(this, "permanentFailureTimeoutSecs", permanentFailureTimeoutSecs);
    ValidationUtils.nonNegative(this, "minReplaceDelaySecs", minReplaceDelaySecs);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(ReplacementFailurePolicy copy) {
    Builder builder = new Builder();
    builder.permanentFailureTimoutSecs = copy.permanentFailureTimeoutSecs;
    builder.minReplaceDelaySecs = copy.minReplaceDelaySecs;
    return builder;
  }

  @JsonProperty("permanent-failure-timeout-secs")
  public Integer getPermanentFailureTimeoutSecs() {
    return permanentFailureTimeoutSecs;
  }

  @JsonProperty("min-replace-delay-Secs")
  public Integer getMinReplaceDelaySecs() {
    return minReplaceDelaySecs;
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  /**
   * {@code ReplacementFailurePolicy} builder static inner class.
   */
  public static final class Builder {
    private Integer permanentFailureTimoutSecs;

    private Integer minReplaceDelaySecs;

    private Builder() {
      this.permanentFailureTimoutSecs = DEFAULT_PERMANENT_FAILURE_DELAY_SECS;
      this.minReplaceDelaySecs = DEFAULT_DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SECS;
    }

    /**
     * Sets the {@code permanentFailureTimoutMin} and returns a reference to this Builder so that the methods can
     * be chained together.
     *
     * @param permanentFailureTimoutSecs the {@code permanentFailureTimoutMin} to set
     * @return a reference to this Builder
     */
    public Builder permanentFailureTimoutSecs(Integer permanentFailureTimoutSecs) {
      this.permanentFailureTimoutSecs = permanentFailureTimoutSecs;
      return this;
    }

    /**
     * Sets the {@code minReplaceDelaySecs} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param minReplaceDelaySecs the {@code minReplaceDelaySecs} to set
     * @return a reference to this Builder
     */
    public Builder minReplaceDelaySecs(Integer minReplaceDelaySecs) {
      this.minReplaceDelaySecs = minReplaceDelaySecs;
      return this;
    }

    /**
     * Returns a {@code ReplacementFailurePolicy} built from the parameters previously set.
     *
     * @return a {@code ReplacementFailurePolicy} built with parameters of this
     * {@code ReplacementFailurePolicy.Builder}
     */
    public ReplacementFailurePolicy build() {
      return new ReplacementFailurePolicy(this);
    }
  }
}
