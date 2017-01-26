package com.mesosphere.sdk.api;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A read-only API for accessing file artifacts (e.g. config templates) for retrieval by executors.
 */
@Path("/v1/artifacts")
public class ArtifactResource {
    private static final String ARTIFACT_URI_FORMAT =
            "http://api.%s.marathon." + ResourceUtils.VIP_HOST_TLD + "/v1/artifacts/template/%s/%s/%s/%s";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ConfigStore<ServiceSpec> configStore;

    /**
     * Returns a valid URL for accessing a config template artifact from a service task.
     * Must be kept in sync with {@link #getTemplate(String, String, String, String)}.
     */
    public static String getTemplateUrl(
            String serviceName, UUID configId, String podType, String taskName, String configName) {
        return String.format(ARTIFACT_URI_FORMAT, serviceName, configId, podType, taskName, configName);
    }

    public ArtifactResource(ConfigStore<ServiceSpec> configStore) {
        this.configStore = configStore;
    }

    /**
     * Produces the content of the requested configuration template, or returns an error if that template doesn't exist
     * or the data couldn't be read. Configuration templates are versioned against configuration IDs, which (despite the
     * similar naming) are not directly related. See also {@link ConfigResource} for more information on configuration
     * IDs.
     *
     * @param configurationId the id of the configuration set to be retrieved from -- this should match the
     *     configuration the task is on. this allows old tasks to continue retrieving old configurations
     * @param podType the name/type of the pod, eg 'index' or 'data'
     * @param taskName the name of the task
     * @param configurationName the name of the configuration to be retrieved
     * @return an HTTP response containing the content of the requested configuration, or an HTTP error
     * @see ConfigResource
     */
    @Path("/template/{configurationId}/{podType}/{taskName}/{configurationName}")
    @GET
    public Response getTemplate(
            @PathParam("configurationId") String configurationId,
            @PathParam("podType") String podType,
            @PathParam("taskName") String taskName,
            @PathParam("configurationName") String configurationName) {
        logger.info("Attempting to fetch template '{}' from config '{}' with pod '{}', task '{}'",
                configurationName, configurationId, podType, taskName);
        UUID uuid;
        try {
            uuid = UUID.fromString(configurationId);
        } catch (IllegalArgumentException ex) {
            logger.warn(String.format(
                    "Failed to parse requested configuration id as a UUID: '%s'", configurationId), ex);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        ServiceSpec serviceSpec;
        try {
            serviceSpec = configStore.fetch(uuid);
        } catch (ConfigStoreException ex) {
            if (ex.getReason() == Reason.NOT_FOUND) {
                logger.warn(String.format("Requested configuration '%s' doesn't exist", configurationId), ex);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            logger.error(String.format(
                    "Failed to fetch requested configuration with id '%s'", configurationId), ex);
            return Response.serverError().build();
        }
        try {
            ConfigFileSpec config = getConfigFile(getTask(getPod(serviceSpec, podType), taskName), configurationName);
            return Response.ok(config.getTemplateContent(), MediaType.TEXT_PLAIN_TYPE).build();
        } catch (Exception ex) {
            logger.warn(String.format(
                    "Couldn't find requested template in config '%s'", configurationId), ex);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    private static PodSpec getPod(ServiceSpec serviceSpec, String podType) throws Exception {
        Optional<PodSpec> podOptional =
                serviceSpec.getPods().stream().filter(pod -> podType.equals(pod.getType())).findFirst();
        if (!podOptional.isPresent()) {
            List<String> availablePodTypes =
                    serviceSpec.getPods().stream().map(podSpec -> podSpec.getType()).collect(Collectors.toList());
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
                    podSpec.getTasks().stream().map(taskSpec -> taskSpec.getName()).collect(Collectors.toList());
            throw new Exception(String.format(
                    "Couldn't find task named '%s' within pod '%s'. Known task names are: %s",
                    taskName, podSpec.getType(), availableTaskNames));
        }
        return taskOptional.get();
    }

    private static ConfigFileSpec getConfigFile(TaskSpec taskSpec, String configName) throws Exception {
        Optional<ConfigFileSpec> configOptional =
                taskSpec.getConfigFiles().stream().filter(config -> configName.equals(config.getName())).findFirst();
        if (!configOptional.isPresent()) {
            List<String> availableConfigNames =
                    taskSpec.getConfigFiles().stream().map(config -> config.getName()).collect(Collectors.toList());
            throw new Exception(String.format(
                    "Couldn't find config named '%s' within task '%s'. Known config names are: %s",
                    configName, taskSpec.getName(), availableConfigNames));
        }
        return configOptional.get();
    }
}
