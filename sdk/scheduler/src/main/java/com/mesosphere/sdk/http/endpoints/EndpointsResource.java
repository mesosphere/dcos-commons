package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.queries.EndpointsQueries;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.TaskInfo;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * A read-only API for accessing information about how to connect to the service.
 */
@Path("/v1/endpoints")
public class EndpointsResource {

  private final StateStore stateStore;

  private final String serviceName;

  private final Map<String, EndpointProducer> customEndpoints = new HashMap<>();

  private final SchedulerConfig schedulerConfig;

  /**
   * Creates a new instance which retrieves task/pod state from the provided {@link StateStore},
   * using the provided {@code serviceName} for endpoint paths.
   */
  public EndpointsResource(
      StateStore stateStore,
      String serviceName,
      SchedulerConfig schedulerConfig)
  {
    this.stateStore = stateStore;
    this.serviceName = serviceName;
    this.schedulerConfig = schedulerConfig;
  }

  /**
   * Adds the provided custom endpoint key/value entry to this instance.
   * <p>
   * This may be used to expose additional endpoint types independently of what's listed in
   * {@link DiscoveryInfo}, such as a Kafka service exposing a Zookeeper path that's separate from
   * the default broker host/port listing.
   * <p>
   * This only supports simple string values in order to ensure that the 'endpoints' endpoint
   * remains relatively consistent across services. For a per-task listing of endpoints, you
   * should provide that information via {@link DiscoveryInfo} in your {@link TaskInfo} and they
   * will appear automatically.
   *
   * @param name             the name of the custom endpoint. custom endpoints take precedence over default
   *                         endpoints of the same name
   * @param endpointProducer the endpoint producer, which will be invoked whenever a user queries the
   *                         list of endpoints
   * @returns this
   */
  public EndpointsResource setCustomEndpoint(String name, EndpointProducer endpointProducer) {
    this.customEndpoints.put(name, endpointProducer);
    return this;
  }

  /**
   * @see EndpointsQueries
   */
  @GET
  public Response getEndpoints() {
    return EndpointsQueries.getEndpoints(stateStore, serviceName, customEndpoints, schedulerConfig);
  }

  /**
   * @see EndpointsQueries
   */
  @Path("/{name}")
  @GET
  public Response getEndpoint(@PathParam("name") String name) {
    return EndpointsQueries.getEndpoint(
        stateStore,
        serviceName,
        customEndpoints,
        name,
        schedulerConfig);
  }
}
