package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.StateQueries;
import com.mesosphere.sdk.http.types.PropertyDeserializer;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;
import com.mesosphere.sdk.state.StateStore;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.InputStream;
import java.util.Optional;

/**
 * An API for reading task and frameworkId state from persistent storage, and resetting the state store cache if one is
 * being used.
 */
@Path("/v1/service")
public class MultiStateResource {

  private final MultiServiceManager multiServiceManager;

  private final PropertyDeserializer propertyDeserializer;

  /**
   * Creates a new StateResource which returns content for runs in the provider.
   */
  public MultiStateResource(
      MultiServiceManager multiServiceManager,
      PropertyDeserializer propertyDeserializer)
  {
    this.multiServiceManager = multiServiceManager;
    this.propertyDeserializer = propertyDeserializer;
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/files")
  @Produces(MediaType.TEXT_PLAIN)
  @GET
  public Response getFiles(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.getFiles(stateStore.get());
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/files/{name}")
  @Produces(MediaType.TEXT_PLAIN)
  @GET
  public Response getFile(
      @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("name") String name)
  {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.getFile(stateStore.get(), name);
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/files/{name}")
  @PUT
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response putFile(
      @PathParam("sanitizedServiceName") String sanitizedServiceName,
      @PathParam("name") String name,
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetails)
  {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.putFile(stateStore.get(), name, uploadedInputStream, fileDetails);
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/zone/tasks")
  @GET
  public Response getTaskNamesToZones(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.getTaskNamesToZones(stateStore.get());
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/zone/tasks/{taskName}")
  @GET
  public Response getTaskNameToZone(
      @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("taskName") String taskName)
  {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.getTaskNameToZone(stateStore.get(), taskName);
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/zone/{podType}/{ip}")
  @GET
  public Response getTaskIPsToZones(
      @PathParam("sanitizedServiceName") String sanitizedServiceName,
      @PathParam("podType") String podType,
      @PathParam("ip") String ip)
  {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.getTaskIPsToZones(stateStore.get(), podType, ip);
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/properties")
  @GET
  public Response getPropertyKeys(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.getPropertyKeys(stateStore.get());
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/properties/{key}")
  @GET
  public Response getProperty(
      @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("key") String key)
  {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.getProperty(stateStore.get(), propertyDeserializer, key);
  }

  /**
   * @see StateQueries
   */
  @Path("{sanitizedServiceName}/state/refresh")
  @PUT
  public Response refreshCache(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
    Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
    if (!stateStore.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return StateQueries.refreshCache(stateStore.get());
  }

  private Optional<StateStore> getStateStore(String sanitizedServiceName) {
    return multiServiceManager
        .getServiceSanitized(sanitizedServiceName)
        .map(AbstractScheduler::getStateStore);
  }
}
