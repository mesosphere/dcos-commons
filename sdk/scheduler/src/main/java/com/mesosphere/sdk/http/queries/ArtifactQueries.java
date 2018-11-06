package com.mesosphere.sdk.http.queries;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.endpoints.ArtifactResource;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.slf4j.Logger;

import javax.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A read-only API for accessing file artifacts (e.g. config templates) for retrieval by pods.
 */
public final class ArtifactQueries {

  private static final Logger LOGGER = LoggingUtils.getLogger(ArtifactQueries.class);

  private ArtifactQueries() {
    // do not instantiate
  }

  /**
   * Produces the content of the requested configuration template, or returns an error if that template doesn't exist
   * or the data couldn't be read. Configuration templates are versioned against configuration IDs, which (despite the
   * similar naming) are not directly related. See also {@link ConfigQueries} for more information on configuration
   * IDs.
   *
   * @param configurationId   the id of the configuration set to be retrieved from -- this should match the
   *                          configuration the task is on. this allows old tasks to continue retrieving old configurations
   * @param podType           the name/type of the pod, eg 'index' or 'data'
   * @param taskName          the name of the task
   * @param configurationName the name of the configuration to be retrieved
   * @return an HTTP response containing the content of the requested configuration, or an HTTP error
   * @see ConfigQueries
   */
  public static Response getTemplate(
      ConfigStore<ServiceSpec> configStore,
      String configurationId,
      String podType,
      String taskName,
      String configurationName)
  {
    LOGGER.info("Attempting to fetch template '{}' from config '{}' with pod '{}', task '{}'",
        configurationName, configurationId, podType, taskName);
    UUID uuid;
    try {
      uuid = UUID.fromString(configurationId);
    } catch (IllegalArgumentException ex) {
      LOGGER.warn(String.format(
          "Failed to parse requested configuration id as a UUID: '%s'", configurationId), ex);
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    ServiceSpec serviceSpec;
    try {
      serviceSpec = configStore.fetch(uuid);
    } catch (ConfigStoreException ex) {
      if (ex.getReason() == Reason.NOT_FOUND) {
        LOGGER.warn(
            String.format(
                "Requested configuration '%s' doesn't exist",
                configurationId
            ),
            ex
        );
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      LOGGER.error(
          String.format(
              "Failed to fetch requested configuration with id '%s'",
              configurationId
          ),
          ex
      );
      return Response.serverError().build();
    }
    try {
      ConfigFileSpec config = getConfigFile(
          getTask(getPod(serviceSpec, podType), taskName),
          configurationName);
      return ResponseUtils.plainOkResponse(config.getTemplateContent());
    } catch (Exception ex) { // SUPPRESS CHECKSTYLE IllegalCatch
      LOGGER.warn(String.format(
          "Couldn't find requested template in config '%s'", configurationId), ex);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  private static PodSpec getPod(ServiceSpec serviceSpec, String podType) throws Exception {
    Optional<PodSpec> podOptional =
        serviceSpec.getPods().stream().filter(pod -> podType.equals(pod.getType())).findFirst();
    if (!podOptional.isPresent()) {
      List<String> availablePodTypes =
          serviceSpec.getPods().stream().map(PodSpec::getType).collect(Collectors.toList());
      throw new Exception(String.format(
          "Couldn't find pod of type '%s'. Known pod types are: %s", podType, availablePodTypes));
    }
    return podOptional.get();
  }

  private static TaskSpec getTask(PodSpec podSpec, String taskName) throws Exception {
    Optional<TaskSpec> taskOptional =
        podSpec.getTasks().stream().filter(task -> taskName.equals(task.getName())).findFirst();
    if (!taskOptional.isPresent()) {
      List<String> availableTaskNames =
          podSpec.getTasks().stream().map(TaskSpec::getName).collect(Collectors.toList());
      throw new Exception(String.format(
          "Couldn't find task named '%s' within pod '%s'. Known task names are: %s",
          taskName, podSpec.getType(), availableTaskNames));
    }
    return taskOptional.get();
  }

  private static ConfigFileSpec getConfigFile(
      TaskSpec taskSpec,
      String configName)
      throws Exception
  {
    Optional<ConfigFileSpec> configOptional = taskSpec
        .getConfigFiles()
        .stream()
        .filter(config -> configName.equals(config.getName()))
        .findFirst();
    if (!configOptional.isPresent()) {
      throw new Exception(String.format(
          "Couldn't find config named '%s' within task '%s'. Known config names are: %s",
          configName,
          taskSpec.getName(),
          taskSpec
              .getConfigFiles()
              .stream()
              .map(ConfigFileSpec::getName)
              .collect(Collectors.toList())
      ));
    }
    return configOptional.get();
  }

  /**
   * Generates template URLs suitable for use with fetching template URLs. Schedulers using
   * {@link ArtifactResource} should use
   * {@link ArtifactResource#getUrlFactory(String, UUID, String, String, String)}. If a different
   * artifact resource is being used, a different corresponding factory should be used as well.
   */
  public interface TemplateUrlFactory {
    String get(UUID configId, String podType, String taskName, String configName);
  }
}
