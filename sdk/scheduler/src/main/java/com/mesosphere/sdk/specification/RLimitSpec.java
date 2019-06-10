package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Representation of an individual rlimit, consisting of a name and optional soft/hard limits.
 * <p>
 * A valid instance of this class has either both limits set or neither, with the further constraint that the soft limit
 * must be less than or equal to the hard limit.
 */
@SuppressWarnings("checkstyle:LocalVariableName")
public class RLimitSpec {

  /**
   * The value representing unlimited for resource limits.
   */
  public static final long RLIMIT_INFINITY = -1;

  private static final Map<String, Protos.RLimitInfo.RLimit.Type> RLIMITS = new HashMap<>();

  static {
    for (Protos.RLimitInfo.RLimit.Type rLimitType : Protos.RLimitInfo.RLimit.Type.values()) {
      RLIMITS.put(rLimitType.toString().replace("RLMT", "RLIMIT"), rLimitType);
    }
  }

  private final String name;

  private final Long soft;

  private final Long hard;

  public RLimitSpec(
      @JsonProperty("name") String name,
      @JsonProperty("soft") Long soft,
      @JsonProperty("hard") Long hard) throws InvalidRLimitException
  {
    this.name = name;
    this.soft = soft;
    this.hard = hard;

    validate();
  }

  public String getName() {
    return name;
  }

  public Optional<Long> getSoft() {
    return Optional.ofNullable(soft);
  }

  public Optional<Long> getHard() {
    return Optional.ofNullable(hard);
  }

  @JsonIgnore
  public Protos.RLimitInfo.RLimit.Type getEnum() {
    return RLIMITS.get(name);
  }

  private void validate() throws InvalidRLimitException {
    if (!RLIMITS.containsKey(name)) {
      throw new InvalidRLimitException(
          name +
              " is not a valid rlimit, expected one of: " +
              RLIMITS.toString() + ". See man setrlimit(2)"
      );
    }

    if (!(soft == null && hard == null) && !(soft != null && hard != null)) {
      throw new InvalidRLimitException(
          "soft and hard rlimits must be either both set or both unset"
      );
    }

    if (soft != null && soft > hard) {
      throw new InvalidRLimitException("soft rlimit must be less than or equal to the hard rlimit");
    }

    if ((soft != null && soft < RLIMIT_INFINITY) || (hard != null && hard < RLIMIT_INFINITY)) {
      throw new InvalidRLimitException("soft and hard rlimits must be positive with the exception"
          + " of " + RLIMIT_INFINITY + " which represents unlimited.");
    }

    if ((soft != null && soft == RLIMIT_INFINITY) ^ (hard != null && hard == RLIMIT_INFINITY)) {
      throw new InvalidRLimitException("both soft and hard limits must be set to "
      + RLIMIT_INFINITY + " which represents unlimited.");
    }
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
   * An exception for errors pertaining to {@link RLimitSpec}.
   */
  public static class InvalidRLimitException extends Exception {
    public InvalidRLimitException(String s) {
      super(s);
    }
  }
}
