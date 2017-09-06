package com.mesosphere.sdk.api;

import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.state.StateStore;

import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Port;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mesosphere.sdk.api.ResponseUtils.jsonOkResponse;
import static com.mesosphere.sdk.api.ResponseUtils.plainOkResponse;

/**
 * A read-only API for accessing information about how to connect to the service.
 */
@Path("/v1/endpoints")
public class EndpointsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointsResource.class);

    private static final String RESPONSE_KEY_DNS = "dns";
    private static final String RESPONSE_KEY_ADDRESS = "address";
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
     * Produces a listing of all endpoint names.
     */
    @GET
    public Response getEndpoints() {
        try {
            Set<String> endpoints = new TreeSet<>();
            endpoints.addAll(customEndpoints.keySet());
            endpoints.addAll(getDiscoveryEndpoints().keySet());
            return jsonOkResponse(new JSONArray(endpoints));
        } catch (Exception ex) {
            LOGGER.error("Failed to fetch list of endpoints", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the content of the specified endpoint.
     *
     * @param name the name of the endpoint whose content should be included
     */
    @Path("/{name}")
    @GET
    public Response getEndpoint(@PathParam("name") String name) {
        try {
            // Check for custom value before emitting any default values:
            EndpointProducer customValue = customEndpoints.get(name);
            if (customValue != null) {
                // Return custom values as plain text. They could be anything.
                return plainOkResponse(customValue.getEndpoint());
            }

            // Fall back to checking default values:
            JSONObject endpoint = getDiscoveryEndpoints().get(name);
            if (endpoint != null) {
                return jsonOkResponse(endpoint);
            }

            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (Exception ex) {
            LOGGER.error(String.format("Failed to fetch endpoint %s", name), ex);
            return Response.serverError().build();
        }
    }

    /**
     * Returns a mapping of endpoint type to host:port (or ip:port) endpoints, endpoint type.
     */
    private Map<String, JSONObject> getDiscoveryEndpoints() throws TaskException {
        Map<String, JSONObject> endpointsByName = new TreeMap<>();
        for (TaskInfo taskInfo : stateStore.fetchTasks()) {
            if (!taskInfo.hasDiscovery()) {
                LOGGER.debug("Task lacks any discovery information, no endpoints to report: {}",
                        taskInfo.getName());
                continue;
            }
            // TODO(mrb): Also extract DiscoveryInfo from executor, when executors get the ability to specify resources
            DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();

            // Autoip hostname:
            String autoIpTaskName = discoveryInfo.hasName() ? discoveryInfo.getName() : taskInfo.getName();
            // Hostname of agent at offer time:
            String nativeHost = new TaskLabelReader(taskInfo).getHostname();
            // get IP address(es) from container status on the latest TaskStatus, if the latest TaskStatus has an IP
            // otherwise use the lastest TaskStatus' IP stored in the stateStore
            List<String> ipAddresses = reconcileIpAddresses(taskInfo.getName());
            for (Port port : discoveryInfo.getPorts().getPortsList()) {
                if (port.getVisibility() != Constants.DISPLAYED_PORT_VISIBILITY) {
                    LOGGER.debug(
                            "Port {} in task {} has {} visibility. {} is needed to be listed in endpoints.",
                            port.getName(), taskInfo.getName(), port.getVisibility(),
                            Constants.DISPLAYED_PORT_VISIBILITY);
                    continue;
                }
                final String hostIpString;
                switch (ipAddresses.size()) {
                case 0:
                    hostIpString = nativeHost;
                    break;
                case 1:
                    hostIpString = ipAddresses.get(0);
                    break;
                default:
                    hostIpString = ipAddresses.toString();
                    break;
                }
                addPortToEndpoints(
                        endpointsByName,
                        serviceName,
                        taskInfo.getName(),
                        port,
                        EndpointUtils.toAutoIpEndpoint(serviceName, autoIpTaskName, port.getNumber()),
                        EndpointUtils.toEndpoint(hostIpString, port.getNumber()));
            }
        }
        return endpointsByName;
    }

    private static List<String> getIpAddresses(Protos.TaskStatus taskStatus) {
        if (taskStatus != null && taskStatus.hasContainerStatus() &&
                taskStatus.getContainerStatus().getNetworkInfosCount() > 0) {
            List<String> ipAddresses = taskStatus.getContainerStatus().getNetworkInfosList().stream()
                    .flatMap(networkInfo -> networkInfo.getIpAddressesList().stream())
                    .map(ipAddress -> ipAddress.getIpAddress())
                    .collect(Collectors.toList());
            return ipAddresses;
        }
        return Collections.emptyList();
    }

    private List<String> reconcileIpAddresses(String taskName) {
        // get the IP addresses from the latest TaskStatus (currentTaskStatus), if that TaskStatus doesn't have an
        // IP address (it's a TASK_KILLED, LOST, etc.) than use the last IP address recorded in the stateStore
        // (this is better than nothing).
        TaskStatus currentTaskStatus = stateStore.fetchStatus(taskName).orElse(null);
        TaskStatus savedTaskStatus = StateStoreUtils.getTaskStatusFromProperty(stateStore, taskName)
                .orElse(null);
        List<String> currentIpAddresses = getIpAddresses(currentTaskStatus);
        return currentIpAddresses.isEmpty() ?
                getIpAddresses(savedTaskStatus) : currentIpAddresses;
    }

    /**
     * Adds information about a {@link Port} to the provided {@code endpointsByName}. Information
     * will be added for any VIPs listed against the {@link Port}'s labels, or if no VIPs are found,
     * the information will be added against the task type.
     *
     * @param endpointsByName the map to write to
     * @param serviceName the name of the parent service
     * @param taskName the name of the task which has the port in question
     * @param taskInfoPort the port being added (from the task's DiscoveryInfo)
     * @param autoipHostPort the host:port value to advertise for connecting to the task over DNS
     * @param ipHostPort the host:port value to advertise for connecting to the task's IP
     * @throws TaskException if no VIPs were found and the task type couldn't be extracted
     */
    private static void addPortToEndpoints(
            Map<String, JSONObject> endpointsByName,
            String serviceName,
            String taskName,
            Port taskInfoPort,
            String autoipHostPort,
            String ipHostPort) throws TaskException {
        if (Strings.isEmpty(taskInfoPort.getName())) {
            // Older tasks may omit the port name in their DiscoveryInfo. In practice this shouldn't happen because
            // tasks that old should have been long updated/relaunched by the time this is invoked, but just in case...
            LOGGER.warn("Missing port name. Old task?: {}", TextFormat.shortDebugString(taskInfoPort));
            return;
        }

        // Search for any VIPs to list the port against:
        Collection<EndpointUtils.VipInfo> vips = AuxLabelAccess.getVIPsFromLabels(taskName, taskInfoPort);

        for (EndpointUtils.VipInfo vip : vips) {
            // VIP found. file host:port against the PORT name.
            addPortAndVipToEndpoints(
                    endpointsByName,
                    taskInfoPort.getName(),
                    autoipHostPort,
                    ipHostPort,
                    EndpointUtils.toVipEndpoint(serviceName, vip));
        }

        // If no VIPs were found, list the port against the port name:
        if (vips.isEmpty() && !Strings.isEmpty(taskInfoPort.getName())) {
            addPortToEndpoints(endpointsByName, taskInfoPort.getName(), autoipHostPort, ipHostPort);
        }
    }

    private static void addPortAndVipToEndpoints(
            Map<String, JSONObject> endpointsByName,
            String portName,
            String autoipHostPort,
            String ipHostPort,
            String vipHostPort) {
        JSONObject vipEndpoint = addPortToEndpoints(endpointsByName, portName, autoipHostPort, ipHostPort);
        vipEndpoint.put(RESPONSE_KEY_VIP, vipHostPort);
    }

    private static JSONObject addPortToEndpoints(
            Map<String, JSONObject> endpointsByName,
            String portName,
            String autoipHostPort,
            String ipHostPort) {
        JSONObject portEndpoint = endpointsByName.get(portName);
        if (portEndpoint == null) {
            portEndpoint = new JSONObject();
            endpointsByName.put(portName, portEndpoint);
        }
        portEndpoint.append(RESPONSE_KEY_DNS, autoipHostPort);
        portEndpoint.append(RESPONSE_KEY_ADDRESS, ipHostPort);
        return portEndpoint;
    }
}
