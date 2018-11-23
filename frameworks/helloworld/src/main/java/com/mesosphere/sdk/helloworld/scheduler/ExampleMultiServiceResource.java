package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.multi.MultiServiceEventClient;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;
import com.mesosphere.sdk.scheduler.multi.ServiceFactory;
import com.mesosphere.sdk.scheduler.multi.ServiceStore;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Example implementation of a resource which dynamically adds and removes services from a
 * dynamic multi-scheduler. This implementation allows users to add/remove example scenario yaml
 * files, referenced by their filename.
 */
@Path("/v1/multi")
public class ExampleMultiServiceResource {

  private static final Logger LOGGER = LoggingUtils.getLogger(ExampleMultiServiceResource.class);

  private static final String YAML_DIR = "hello-world-scheduler/";

  private static final String YAML_EXT = ".yml";

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private final MultiServiceManager multiServiceManager;

  private final ServiceStore serviceStore;

  ExampleMultiServiceResource(
      SchedulerConfig schedulerConfig,
      FrameworkConfig frameworkConfig,
      Persister persister,
      Collection<Scenario.Type> scenarios,
      MultiServiceManager multiServiceManager)
  {
    this.multiServiceManager = multiServiceManager;
    ServiceFactory serviceFactory = context -> {
      // Generate a ServiceSpec from the provided yaml file name, which is in the context
      ContextData contextData = ContextData.deserialize(context);

      File yamlFile = getYamlFile(contextData.yamlName);

      // Render service specs, with any provided parameters overriding the scheduler env:
      Map<String, String> serviceParameters = new HashMap<>();
      serviceParameters.putAll(System.getenv());
      serviceParameters.putAll(contextData.envOverride);

      RawServiceSpec rawServiceSpec =
          RawServiceSpec.newBuilder(yamlFile).setEnv(serviceParameters).build();
      ServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(
          rawServiceSpec, schedulerConfig, serviceParameters, yamlFile.getParentFile())
          // Override any framework-level params in the servicespec (role, principal, ...) with ours:
          .setMultiServiceFrameworkConfig(frameworkConfig)
          .build();

      SchedulerBuilder builder = DefaultScheduler
          .newBuilder(serviceSpec, schedulerConfig, persister)
          .setPlansFrom(rawServiceSpec)
          .enableMultiService(frameworkConfig.getFrameworkName());
      return Scenario
          .customize(builder, Optional.of(frameworkConfig.getFrameworkName()), scenarios)
          .build();
    };
    this.serviceStore = new ServiceStore(persister, serviceFactory);
  }

  /**
   * Returns a list of all available YAML examples, suitable for launching an example service against.
   */
  @Path("yaml")
  @GET
  public Response listYamls() {
    JSONArray yamls = new JSONArray();

    // Sort names alphabetically:
    Collection<File> files = new TreeSet<>(FileUtils.listFiles(
        new File(YAML_DIR),
        new AbstractFileFilter() {
          @Override
          public boolean accept(File dir, String name) {
            return name.endsWith(YAML_EXT);
          }
        },
        null /* do not iterate subdirs */));
    for (File f : files) {
      String name = f.getName();
      // Remove .yml extension in response:
      yamls.put(name.substring(0, name.length() - YAML_EXT.length()));
    }

    return ResponseUtils.jsonOkResponse(yamls);
  }

  /**
   * Returns a list of added active services.
   */
  @GET
  public Response listServices() {
    JSONArray services = new JSONArray();
    for (String serviceName : multiServiceManager.getServiceNames()) {
      JSONObject service = new JSONObject();
      service.put("service", serviceName);

      Optional<AbstractScheduler> scheduler = multiServiceManager.getService(serviceName);
      // Technically, the scheduler could disappear if it's uninstalled while we iterate over service names
      if (!scheduler.isPresent()) {
        continue;
      }

      // YAML file path
      try {
        Optional<byte[]> context = serviceStore.get(serviceName);
        // SUPPRESS CHECKSTYLE MultipleStringLiteral
        context.ifPresent(bytes -> service.put("yaml", ContextData.deserialize(bytes).yamlName));
      } catch (PersisterException e) {
        LOGGER.error(String.format("Failed to get yaml filename for service %s", serviceName), e);
      }

      // Detect uninstall-in-progress by class type
      service.put("uninstall", scheduler.get() instanceof UninstallScheduler);

      services.put(service);
    }
    return ResponseUtils.jsonOkResponse(services);
  }

  /**
   * Triggers uninstall of a specified service. Once it has finished uninstalling, it will automatically be removed
   * from the set of active services.
   */
  @Path("{serviceName}")
  @DELETE
  public Response uninstall(@PathParam("serviceName") String serviceName) {
    multiServiceManager.uninstallService(serviceName);
    return ResponseUtils.plainOkResponse("Triggered removal of service: " + serviceName);
  }

  /**
   * Accepts a new service to be launched immediately, using the provided example yaml name.
   * <p>
   * <p>See {@link #listYamls()} for a list of available yaml files.
   */
  @Path("{serviceName}")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response add(
      @PathParam("serviceName") String serviceName,
      @QueryParam("yaml") String yamlName,
      Map<String, String> envOverride)
  {
    // Create an AbstractScheduler using the specified file, bailing if it doesn't work.
    AbstractScheduler service;
    try {
      service = serviceStore.put(new ContextData(serviceName, yamlName, envOverride).serialize());
    } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
      LOGGER.error("Failed to generate or persist service", e);
      return ResponseUtils.plainResponse(
          String.format("Failed to generate or persist service: %s", e.getMessage()),
          Response.Status.BAD_REQUEST);
    }
    multiServiceManager.putService(service);

    try {
      JSONObject obj = new JSONObject();
      // SUPPRESS CHECKSTYLE MultipleStringLiteral
      obj.put("name", service.getServiceSpec().getName());
      obj.put("yaml", yamlName);
      return ResponseUtils.jsonOkResponse(obj);
    } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
      // This should never happen.
      LOGGER.error("JSON error when encoding response for adding or updating service", e);
      return Response.serverError().build();
    }
  }

  /**
   * Recovers any previously added service instances and re-adds them to the internal MultiServiceManager.
   * <p>
   * <p>Recovery should always be invoked once during startup to rebuild any previously-added services. If no services
   * were active or if this is the initial launch of the scheduler, then this is effectively a no-op.
   */
  public void recover() throws PersisterException {
    for (AbstractScheduler service : serviceStore.recover()) {
      multiServiceManager.putService(service);
    }
  }

  /**
   * Returns an uninstall callback suitable for passing to the MultiServiceEventClient.
   */
  public MultiServiceEventClient.UninstallCallback getUninstallCallback() {
    return serviceStore.getUninstallCallback();
  }

  /**
   * Returns the specified example YAML file from the scheduler filesystem.
   */
  public static File getYamlFile(String yamlName) {
    return new File(YAML_DIR, yamlName + YAML_EXT);
  }

  /**
   * Data type for storing the service name and yaml name which serializes to JSON.
   */
  private static final class ContextData {
    private final String serviceName;

    private final String yamlName;

    private final Map<String, String> envOverride;

    private ContextData(String serviceName, String yamlName, Map<String, String> envOverride) {
      this.serviceName = serviceName;
      this.yamlName = yamlName;
      this.envOverride = new TreeMap<>(envOverride);
    }

    @SuppressWarnings("checkstyle:MultipleStringLiterals")
    private static ContextData deserialize(byte[] context) {
      JSONObject obj = new JSONObject(new String(context, CHARSET));
      Map<String, String> params = new TreeMap<>();
      JSONArray jsonParams = obj.getJSONArray("params");
      for (int i = 0; i < jsonParams.length(); ++i) {
        JSONObject jsonParam = jsonParams.getJSONObject(i);
        params.put(jsonParam.getString("key"), jsonParam.getString("value"));
      }
      return new ContextData(obj.getString("name"), obj.getString("yaml"), params);
    }

    private byte[] serialize() {
      JSONObject obj = new JSONObject();
      obj.put("name", serviceName);
      obj.put("yaml", yamlName);
      JSONArray params = new JSONArray();
      for (Map.Entry<String, String> entry : envOverride.entrySet()) {
        JSONObject jsonEntry = new JSONObject();
        jsonEntry.put("key", entry.getKey());
        jsonEntry.put("value", entry.getValue());
        params.put(jsonEntry);
      }
      obj.put("params", params);
      return obj.toString().getBytes(CHARSET);
    }
  }
}
