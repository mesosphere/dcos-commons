package com.mesosphere.sdk.specification.yaml;

import com.mesosphere.sdk.offer.LoggingUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root of the parsed YAML object model.
 */
public final class RawServiceSpec {
  private static final Logger LOGGER = LoggingUtils.getLogger(RawServiceSpec.class);

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  static {
    // If the user provides duplicate fields (e.g. 'count' twice), throw an error instead of silently dropping data:
    YAML_MAPPER.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    YAML_MAPPER.enable(JsonParser.Feature.ALLOW_YAML_COMMENTS);
  }

  private final String name;

  private final String webUrl;

  private final RawScheduler scheduler;

  private final WriteOnceLinkedHashMap<String, RawPod> pods;

  private final WriteOnceLinkedHashMap<String, RawPlan> plans;

  @JsonCreator
  private RawServiceSpec(
      @JsonProperty("name") String name,
      @JsonProperty("web-url") String webUrl,
      @JsonProperty("scheduler") RawScheduler scheduler,
      @JsonProperty("pods") WriteOnceLinkedHashMap<String, RawPod> pods,
      @JsonProperty("plans") WriteOnceLinkedHashMap<String, RawPlan> plans)
  {
    this.name = name;
    this.webUrl = webUrl;
    this.scheduler = scheduler;
    this.pods = pods;
    this.plans = plans;
  }

  /**
   * Generates a {@link RawServiceSpec} object representation from the provided plain YAML content.
   * Mustache handling is not done here. For that you need {@link #newBuilder(File)}.
   */
  public static RawServiceSpec fromBytes(byte[] yamlContent)
      throws IOException
  {
    return YAML_MAPPER.readValue(yamlContent, RawServiceSpec.class);
  }

  public static Builder newBuilder(File pathToYamlTemplate) {
    return new Builder(pathToYamlTemplate);
  }

  public String getName() {
    return name;
  }

  public String getWebUrl() {
    return webUrl;
  }

  public RawScheduler getScheduler() {
    return scheduler;
  }

  public LinkedHashMap<String, RawPod> getPods() {
    return pods;
  }

  public WriteOnceLinkedHashMap<String, RawPlan> getPlans() {
    return plans;
  }

  /**
   * Handles mustache rendering of {@link RawServiceSpec}s based on the Scheduler's environment variables.
   */
  public static final class Builder {

    private final File pathToYamlTemplate;

    private Map<String, String> env;

    private boolean isRenderingStrict = false;

    private Builder(File pathToYamlTemplate) {
      this.pathToYamlTemplate = pathToYamlTemplate;
      this.env = System.getenv();
    }

    /**
     * Overrides use of the scheduler's environment variables with the provided custom map.
     */
    public Builder setEnv(Map<String, String> env) {
      this.env = env;
      return this;
    }

    /**
     * Set whether the rendering should be strict.
     */
    public Builder enableStrictRendering() {
      this.isRenderingStrict = true;
      return this;
    }

    /**
     * Returns the object representation of the provided template data, with any mustache templating resolved using
     * the provided environment map.
     */
    public RawServiceSpec build() throws Exception {
      // We allow missing values. For example, the service principal may be left empty, in which case we use a
      // reasonable default principal.
      List<TemplateUtils.MissingValue> missingValues = new ArrayList<>();
      String yamlWithEnv = TemplateUtils.renderMustache(
          pathToYamlTemplate.getName(),
          FileUtils.readFileToString(pathToYamlTemplate, StandardCharsets.UTF_8),
          env,
          missingValues);
      LOGGER.info("Rendered ServiceSpec from {}:\nMissing template values: {}\n{}",
          pathToYamlTemplate.getAbsolutePath(), missingValues, yamlWithEnv);

      if (this.isRenderingStrict) {
        TemplateUtils.validateMissingValues(pathToYamlTemplate.getName(), env, missingValues);
      }

      return fromBytes(yamlWithEnv.getBytes(StandardCharsets.UTF_8));
    }
  }
}
