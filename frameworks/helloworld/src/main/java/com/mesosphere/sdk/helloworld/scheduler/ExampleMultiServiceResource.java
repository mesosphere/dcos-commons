package com.mesosphere.sdk.helloworld.scheduler;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.TreeSet;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

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

/**
 * Example implementation of a resource which dynamically adds and removes services from a dynamic multi-scheduler.
 * This implementation allows users to add/remove example scenario yaml files, referenced by their filename.
 */
@Path("/v1/multi")
public class ExampleMultiServiceResource {

    private static final Logger LOGGER = LoggingUtils.getLogger(ExampleMultiServiceResource.class);

    private static final String YAML_DIR = "hello-world-scheduler/";
    private static final String YAML_EXT = ".yml";
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private final ServiceFactory factory = new ServiceFactory() {
        @Override
        public AbstractScheduler buildService(String serviceId, byte[] context) throws Exception {
            // Generate a ServiceSpec from the provided yaml file name, which is in the context
            File yamlFile = getYamlFile(fromContext(context));
            RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlFile).build();
            ServiceSpec serviceSpec =
                    DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, yamlFile.getParentFile()).build();

            // Override the service name in the yaml file with the serviceId provided by the user.
            serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec).name(serviceId).build();

            SchedulerBuilder builder = DefaultScheduler.newBuilder(serviceSpec, schedulerConfig, persister)
                    .setPlansFrom(rawServiceSpec)
                    .enableMultiService(frameworkName);
            return Scenario.customize(builder, scenarios).build();
        }
    };

    private final SchedulerConfig schedulerConfig;
    private final String frameworkName;
    private final Persister persister;
    private final Collection<Scenario.Type> scenarios;
    private final MultiServiceManager multiServiceManager;
    private final ServiceStore serviceStore;

    ExampleMultiServiceResource(
            SchedulerConfig schedulerConfig,
            String frameworkName,
            Persister persister,
            Collection<Scenario.Type> scenarios,
            MultiServiceManager multiServiceManager) {
        this.schedulerConfig = schedulerConfig;
        this.frameworkName = frameworkName;
        this.persister = persister;
        this.scenarios = scenarios;
        this.multiServiceManager = multiServiceManager;
        this.serviceStore = new ServiceStore(persister, factory);
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
        for (String serviceId : multiServiceManager.getServiceNames()) {
            JSONObject service = new JSONObject();
            service.put("service", serviceId);

            Optional<AbstractScheduler> scheduler = multiServiceManager.getService(serviceId);
            // Technically, the scheduler could disappear if it's uninstalled while we iterate over service names
            if (!scheduler.isPresent()) {
                continue;
            }

            // YAML file path
            try {
                Optional<byte[]> context = serviceStore.get(serviceId);
                if (context.isPresent()) {
                    service.put("yaml", fromContext(context.get()));
                }
            } catch (PersisterException e) {
                LOGGER.error(String.format("Failed to get yaml filename for service %s", serviceId), e);
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
    @Path("{serviceId}")
    @DELETE
    public Response uninstall(@PathParam("serviceId") String serviceId) {
        multiServiceManager.uninstallService(serviceId);
        return ResponseUtils.plainOkResponse("Triggered removal of service: " + serviceId);
    }

    /**
     * Accepts a new service to be launched immediately, using the provided example yaml name.
     *
     * <p>See {@link #listYamls()} for a list of available yaml files.
     */
    @Path("{serviceId}")
    @POST
    public Response add(@PathParam("serviceId") String serviceId, @QueryParam("yaml") String yamlName) {
        // Create an AbstractScheduler using the specified file, bailing if it doesn't work.
        AbstractScheduler service;
        try {
            service = serviceStore.put(serviceId, toContext(yamlName));
        } catch (Exception e) {
            LOGGER.error("Failed to generate or persist service", e);
            return ResponseUtils.plainResponse(
                    String.format("Failed to generate or persist service: %s", e.getMessage()),
                    Response.Status.BAD_REQUEST);
        }
        multiServiceManager.putService(service);

        try {
            JSONObject obj = new JSONObject();
            obj.put("name", service.getServiceSpec().getName());
            obj.put("yaml", yamlName);
            return ResponseUtils.jsonOkResponse(obj);
        } catch (Exception e) {
            // This should never happen.
            LOGGER.error("JSON error when encoding response for adding or updating service", e);
            return Response.serverError().build();
        }
    }

    /**
     * Recovers any previously added service instances and re-adds them to the internal MultiServiceManager.
     *
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
     * Deserializes the yaml file name from the provided serialized "context" from the ServiceStore.
     */
    private static String fromContext(byte[] context) {
        return new String(context, CHARSET);
    }

    /**
     * Serializes the yaml file name to a serialized "context" to be stored in the ServiceStore.
     */
    private byte[] toContext(String yamlName) {
        return yamlName.getBytes(CHARSET);
    }
}
