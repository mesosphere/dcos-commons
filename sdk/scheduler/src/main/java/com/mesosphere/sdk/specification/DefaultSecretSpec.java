package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link SecretSpec}.
 */
public final class DefaultSecretSpec implements SecretSpec {

  /**
   * Regexp for valid filePath:
   * sub-pattern = [.a-zA-Z0-9]+([.a-zA-Z0-9_-]*[/\\\\]*)*
   * (sub-pattern)?  = either NULL, or sub-pattern.  So It can be Null.
   * No leading slash character is allowed!
   */
  private static final Pattern VALID_FILE_PATH_PATTERN =
      Pattern.compile("([.a-zA-Z0-9]+([.a-zA-Z0-9_-]*[/\\\\]*)*)?");

  private final String secretPath;

  private final String envKey;

  private final String filePath;

  @JsonCreator
  private DefaultSecretSpec(
      @JsonProperty("secret") String secretPath,
      @JsonProperty("env-key") String envKey,
      @JsonProperty("file") String filePath)
  {
    this.secretPath = secretPath;
    this.envKey = envKey;
    this.filePath = filePath;
  }

  private DefaultSecretSpec(Builder builder) {
    this(builder.secretPath, builder.envKey, builder.filePath);

    ValidationUtils.nonEmpty(this, "secretPath", secretPath);
    ValidationUtils.matchesRegexAllowNull(this, "filePath", filePath, VALID_FILE_PATH_PATTERN);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @JsonProperty("secret")
  @Override
  public String getSecretPath() {
    return secretPath;
  }

  @JsonProperty("env-key")
  @Override
  public Optional<String> getEnvKey() {
    return Optional.ofNullable(envKey);
  }

  @JsonProperty("file")
  @Override
  public Optional<String> getFilePath() {
    return Optional.ofNullable(filePath);
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
   * {@link DefaultSecretSpec} builder static inner class.
   */
  public static final class Builder {

    private String secretPath;

    private String envKey;

    private String filePath;

    private Builder() {}

    public Builder secretPath(String secretPath) {
      this.secretPath = secretPath;
      return this;
    }

    public Builder envKey(String envKey) {
      this.envKey = envKey;
      return this;
    }

    public Builder filePath(String filePath) {
      this.filePath = filePath;
      return this;
    }

    public DefaultSecretSpec build() {
      return new DefaultSecretSpec(this);
    }
  }
}
