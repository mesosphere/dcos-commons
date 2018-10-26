package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Default implementation of {@link HealthCheckSpec}.
 */
public final class DefaultHealthCheckSpec implements HealthCheckSpec {

  private final String command;

  private final Integer maxConsecutiveFailures;

  private final Integer delay;

  private final Integer interval;

  private final Integer timeout;

  private final Integer gracePeriod;

  @JsonCreator
  private DefaultHealthCheckSpec(
      @JsonProperty("command") String command,
      @JsonProperty("max-consecutive-failures") Integer maxConsecutiveFailures,
      @JsonProperty("delay") Integer delay,
      @JsonProperty("interval") Integer interval,
      @JsonProperty("timeout") Integer timeout,
      @JsonProperty("grace-period") Integer gracePeriod,
      // TODO(nickbp): Remove this parameter on or after Jan 2019.
      //               Needed for upgrade compatibility from SDK 0.51.0 and earlier.
      @JsonProperty("gracePeriod") Integer gracePeriodForUpgradeCompatibility)
  {
    this.command = command;
    this.maxConsecutiveFailures = maxConsecutiveFailures;
    this.delay = delay;
    this.interval = interval;
    this.timeout = timeout;
    // "grace-period": Used in 0.52.0 and later. Try this first.
    // "gracePeriod": Used in 0.51.0 and earlier. Fall back to this only if "grace-period" isn't set.
    this.gracePeriod = gracePeriod != null ? gracePeriod : gracePeriodForUpgradeCompatibility;
  }

  private DefaultHealthCheckSpec(Builder builder) {
    this(
        builder.command,
        builder.maxConsecutiveFailures,
        builder.delay,
        builder.interval,
        builder.timeout,
        builder.gracePeriod,
        builder.gracePeriod);
    ValidationUtils.nonEmpty(this, "command", command);
    ValidationUtils.atLeastOne(this, "maxConsecutiveFailures", maxConsecutiveFailures);
    ValidationUtils.nonNegative(this, "delay", delay);
    ValidationUtils.nonNegative(this, "interval", interval);
    ValidationUtils.nonNegative(this, "timeout", timeout);
    ValidationUtils.nonNegative(this, "gracePeriod", gracePeriod);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(DefaultHealthCheckSpec copy) {
    Builder builder = new Builder();
    builder.command = copy.command;
    builder.maxConsecutiveFailures = copy.maxConsecutiveFailures;
    builder.delay = copy.delay;
    builder.interval = copy.interval;
    builder.timeout = copy.timeout;
    builder.gracePeriod = copy.gracePeriod;
    return builder;
  }

  @Override
  @JsonProperty("command")
  public String getCommand() {
    return command;
  }

  @Override
  @JsonProperty("max-consecutive-failures")
  public Integer getMaxConsecutiveFailures() {
    return maxConsecutiveFailures;
  }

  @Override
  @JsonProperty("delay")
  public Integer getDelay() {
    return delay;
  }

  @Override
  @JsonProperty("interval")
  public Integer getInterval() {
    return interval;
  }

  @Override
  @JsonProperty("timeout")
  public Integer getTimeout() {
    return timeout;
  }

  @Override
  @JsonProperty("grace-period")
  public Integer getGracePeriod() {
    return gracePeriod;
  }

  /**
   * Produce a "gracePeriod" value in addition to "grace-period". This ensures downgrade compatibility to SDK 0.51.0
   * (and earlier).
   * <p>
   * TODO(nickbp): Remove this function on or after Jan 2019.
   */
  @JsonProperty("gracePeriod")
  public Integer getGracePeriodForDowngradeCompatibility() {
    return gracePeriod;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
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
   * {@code DefaultHealthCheckSpec} builder static inner class.
   */
  public static final class Builder {
    private String command;

    private Integer maxConsecutiveFailures;

    private Integer delay;

    private Integer interval;

    private Integer timeout;

    private Integer gracePeriod;

    private Builder() {}

    /**
     * Sets the {@code command} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param command the {@code command} to set
     * @return a reference to this Builder
     */
    public Builder command(String command) {
      this.command = command;
      return this;
    }

    /**
     * Sets the {@code maxConsecutiveFailures} and returns a reference to this Builder so that the methods can be
     * chained together.
     *
     * @param maxConsecutiveFailures the {@code maxConsecutiveFailures} to set
     * @return a reference to this Builder
     */
    public Builder maxConsecutiveFailures(Integer maxConsecutiveFailures) {
      this.maxConsecutiveFailures = maxConsecutiveFailures;
      return this;
    }

    /**
     * Sets the {@code delay} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param delay the {@code delay} to set
     * @return a reference to this Builder
     */
    public Builder delay(Integer delay) {
      this.delay = delay;
      return this;
    }

    /**
     * Sets the {@code interval} and returns a reference to this Builder so that the methods can be chained
     * together.
     *
     * @param interval the {@code interval} to set
     * @return a reference to this Builder
     */
    public Builder interval(Integer interval) {
      this.interval = interval;
      return this;
    }

    /**
     * Sets the {@code timeout} and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param timeout the {@code timeout} to set
     * @return a reference to this Builder
     */
    public Builder timeout(Integer timeout) {
      this.timeout = timeout;
      return this;
    }

    /**
     * Sets the {@code gracePeriod} and returns a reference to this Builder so that the methods can be chained
     * together.
     *
     * @param gracePeriod the {@code gracePeriod} to set
     * @return a reference to this Builder
     */
    public Builder gracePeriod(Integer gracePeriod) {
      this.gracePeriod = gracePeriod;
      return this;
    }

    /**
     * Returns a {@link DefaultHealthCheckSpec} built from the parameters previously set.
     */
    public DefaultHealthCheckSpec build() {
      return new DefaultHealthCheckSpec(this);
    }
  }
}
