package com.mesosphere.sdk.api;

import java.util.*;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Splitter;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.specification.DefaultResourceSet;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Port;
import org.apache.mesos.Protos.TaskInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A read-only API for accessing information about how to connect to the service.
 */
@Path("/v1/endpoints")
public class EndpointsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointsResource.class);

    private static final String RESPONSE_KEY_DIRECT = "direct";
    private static final String RESPONSE_KEY_VIP = "vip";

    private final StateStore stateStore;
    private final String serviceName;
    private final Map<String, EndpointProducer> customEndpoints = new HashMap<>();

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore},
     * using the provided {@code serviceName} for endpoint paths.
     */
    public EndpointsResource(StateStore stateStore, String serviceName) {
        this.stateStore = stateStore;
        this.serviceName = serviceName;
    }

    /**
     * Adds the provided custom endpoint key/value entry to this instance.
     *
     * This may be used to expose additional endpoint types independently of what's listed in
     * {@link DiscoveryInfo}, such as a Kafka service exposing a Zookeeper path that's separate from
     * the default broker host/port listing.
     *
     * This only supports simple string values in order to ensure that the 'endpoints' endpoint
     * remains relatively consistent across services. For a per-task listing of endpoints, you
     * should provide that information via {@link DiscoveryInfo} in your {@link TaskInfo} and they
     * will appear automatically.
     *
     * @param name the name of the custom endpoint. custom endpoints take precedence over default
     *     endpoints of the same name
     * @param endpointProducer the endpoint producer, which will be invoked whenever a user queries the
     *     list of endpoints
     * @returns this
     */
    public EndpointsResource setCustomEndpoint(String name, EndpointProducer endpointProducer) {
        this.customEndpoints.put(name, endpointProducer);
        return this;
    }

    /**
     * Produces a listing of all endpoints, with an optional format argument.
     *
     * @param format the format option, which should either be empty or "ip"
     */
    @GET
    public Response getEndpoints(@QueryParam("format") String format) {
        try {
            List<String> endpoints = new ArrayList<>();

            // Custom values take precedence:
            for (Map.Entry<String, EndpointProducer> entry : customEndpoints.entrySet()) {
                endpoints.add(entry.getKey());
            }
            // Add default values (when they don't collide with custom values):
            for (Map.Entry<String, JSONObject> endpointType :
                    getDiscoveryEndpoints(
                            serviceName,
                            stateStore.fetchTasks(),
                            isNativeFormat(format)).entrySet()) {
                if (!endpoints.contains(endpointType.getKey())) {
                    endpoints.add(endpointType.getKey());
                }
            }

            return Response.ok(new JSONArray(endpoints).toString(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            LOGGER.error(String.format("Failed to fetch list of endpoints with format %s", format), ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the content of the specified endpoint, with an optional format argument.
     *
     * @param name the name of the endpoint whose content should be included
     * @param format the format option, which should either be {@code null}/empty or 'native'
     */
    @Path("/{name}")
    @GET
    public Response getEndpoint(
            @PathParam("name") String name,
            @QueryParam("format") String format) {
        try {
            // Check for custom value before emitting any default values:
            EndpointProducer customValue = customEndpoints.get(name);
            if (customValue != null) {
                return Response.ok(customValue.getEndpoint(), MediaType.TEXT_PLAIN).build();
            }
            JSONObject endpoint = getDiscoveryEndpoints(
                    serviceName,
                    stateStore.fetchTasks(),
                    isNativeFormat(format))
                    .get(name);
            if (endpoint == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(endpoint.toString(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            LOGGER.error(String.format("Failed to fetch endpoint %s with format %s", name, format), ex);
            return Response.serverError().build();
        }
    }

    /**
     * Returns whether the provided {@code format} argument indicates that native hosts (instead of
     * Mesos-DNS hosts) should be produced.
     *
     * @param format the value of the 'format' option, or a {@code null}/empty string if none was provided
     */
    private static boolean isNativeFormat(String format) {
        return format != null && format.equals("native");
    }

    /**
     * Returns a mapping of endpoint type to host:port (or ip:port) endpoints, endpoint type.
     */
    private static Map<String, JSONObject> getDiscoveryEndpoints(
            String serviceName,
            Collection<TaskInfo> taskInfos,
            boolean isNativeFormat) throws TaskException {
        Map<String, JSONObject> endpointsByName = new HashMap<>();
        for (TaskInfo taskInfo : taskInfos) {
            if (!taskInfo.hasDiscovery()) {
                LOGGER.info("Task lacks any discovery information, no endpoints to report: {}",
                        taskInfo.getName());
                continue;
            }
            // TODO(mrb): Also extract DiscoveryInfo from executor, when executors get the ability to specify resources
            DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
            if (discoveryInfo.getVisibility() != DefaultResourceSet.PUBLIC_VIP_VISIBILITY) {
                LOGGER.info("Task discovery information has {} visibility, {} needed to be included in endpoints: {}",
                        discoveryInfo.getVisibility(), DefaultResourceSet.PUBLIC_VIP_VISIBILITY, taskInfo.getName());
                continue;
            }
            final String directHost;
            if (isNativeFormat) {
                // Hostname of agent at offer time:
                directHost = CommonTaskUtils.getHostname(taskInfo);
            } else {
                // Mesos DNS hostname:
                directHost = String.format("%s.%s.mesos", taskInfo.getName(), serviceName);
            }

            for (Port port : discoveryInfo.getPorts().getPortsList()) {
                // host:port to include in 'direct' array(s):
                final String directHostPort = String.format("%s:%d", directHost, port.getNumber());
                addPortToEndpoints(
                        serviceName,
                        endpointsByName,
                        taskInfo,
                        directHostPort,
                        port.getLabels().getLabelsList());
            }
        }
        return endpointsByName;
    }

    /**
     * Adds information about a {@link Port} to the provided {@code endpointsByName}. Information
     * will be added for any VIPs listed against the {@link Port}'s labels, or if no VIPs are found,
     * the information will be added against the task type.
     *
     * @param endpointsByName the map to write to
     * @param taskInfo the task which has the port
     * @param directHostPort the host:port value to advertise for directly connecting to the task
     * @param portLabels list of any {@link Label}s which were present in the {@link Port}
     * @throws TaskException if no VIPs were found and the task type couldn't be extracted
     */
    private static void addPortToEndpoints(
            String serviceName,
            Map<String, JSONObject> endpointsByName,
            TaskInfo taskInfo,
            String directHostPort,
            List<Label> portLabels) throws TaskException {
        // Search for any VIPs to add the above host:port against:
        boolean foundAnyVips = false;
        for (Label label : portLabels) {
            VIPInfo vipInfo = VIPInfo.parse(taskInfo.getName(), label);
            if (vipInfo == null) {
                continue;
            }
            // VIP found. file host:port against the VIP name.
            foundAnyVips = true;

            JSONObject vipEndpoint = endpointsByName.get(vipInfo.name);
            if (vipEndpoint == null) {
                vipEndpoint = new JSONObject();
                endpointsByName.put(vipInfo.name, vipEndpoint);
            }

            // append entry to 'direct' array for this task:
            vipEndpoint.append(RESPONSE_KEY_DIRECT, directHostPort);
            // populate 'vip' field if not yet populated (due to another task with the same vip):
            vipEndpoint.put(RESPONSE_KEY_VIP, String.format("%s.%s.%s:%d",
                    vipInfo.name, serviceName, ResourceUtils.VIP_HOST_TLD, vipInfo.port));
        }

        if (!foundAnyVips) {
            // No VIPs found for this port. file direct host:port against task type.
            final String taskType = CommonTaskUtils.getType(taskInfo);

            JSONObject taskEndpoint = endpointsByName.get(taskType);
            if (taskEndpoint == null) {
                taskEndpoint = new JSONObject();
                endpointsByName.put(taskType, taskEndpoint);
            }

            // append entry to 'direct' array for this task:
            taskEndpoint.append(RESPONSE_KEY_DIRECT, directHostPort);
        }
    }

    /**
     * Struct for VIP name + port, e.g. 'broker' and 9092.
     */
    private static class VIPInfo {
        private final String name;
        private final int port;

        private VIPInfo(String name, int port) {
            this.name = name;
            this.port = port;
        }

        private static VIPInfo parse(String taskName, Label label) {
            if (!label.getKey().startsWith(ResourceUtils.VIP_PREFIX)) {
                return null;
            }
            List<String> namePort = Splitter.on(':').splitToList(label.getValue());
            if (namePort.size() != 2) {
                LOGGER.error("Task {}'s VIP value for {} is invalid, expected 2 components but got {}: {}",
                        taskName, label.getKey(), namePort.size(), label.getValue());
                return null;
            }
            int vipPort;
            try {
                vipPort = Integer.parseInt(namePort.get(1));
            } catch (NumberFormatException e) {
                LOGGER.error(String.format(
                        "Unable to Task %s's VIP port from %s as an int",
                        taskName, label.getValue()), e);
                return null;
            }
            return new VIPInfo(namePort.get(0), vipPort);
        }
    }
}
