package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.queries.StateQueries;
import com.mesosphere.sdk.http.types.PropertyDeserializer;
import com.mesosphere.sdk.state.FrameworkStore;
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

/**
 * An API for reading task and frameworkId state from persistent storage, and resetting the state store cache if one is
 * being used.
 */
@Path("/v1/state")
public class StateResource {

  private final FrameworkStore frameworkStore;

  private final StateStore stateStore;

  private final PropertyDeserializer propertyDeserializer;

  /**
   * Creates a new StateResource which can deserialize Properties. Callers will be able to view
   * the content of individual Properties.
   *
   * @param frameworkStore       the source of framework id data to be returned to callers
   * @param stateStore           the source of data to be returned to callers
   * @param propertyDeserializer a deserializer which can turn any Property in the provided
   *                             {@code stateStore} to valid JSON
   */
  public StateResource(
      FrameworkStore frameworkStore,
      StateStore stateStore,
      PropertyDeserializer propertyDeserializer)
  {
    this.frameworkStore = frameworkStore;
    this.stateStore = stateStore;
    this.propertyDeserializer = propertyDeserializer;
  }

  /**
   * @see StateQueries
   */
  @Path("/frameworkId")
  @GET
  public Response getFrameworkId() {
    return StateQueries.getFrameworkId(frameworkStore);
  }

  /**
   * @see StateQueries
   */
  @Path("/files")
  @Produces(MediaType.TEXT_PLAIN)
  @GET
  public Response getFiles() {
    return StateQueries.getFiles(stateStore);
  }

  /**
   * @see StateQueries
   */
  @Path("/files/{name}")
  @Produces(MediaType.TEXT_PLAIN)
  @GET
  public Response getFile(@PathParam("name") String name) {
    return StateQueries.getFile(stateStore, name);
  }

  /**
   * @see StateQueries
   */
  @Path("/files/{name}")
  @PUT
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response putFile(
      @PathParam("name") String name,
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetails)
  {
    return StateQueries.putFile(stateStore, name, uploadedInputStream, fileDetails);
  }

  /**
   * @see StateQueries
   */
  @Path("/zone/tasks")
  @GET
  public Response getTaskNamesToZones() {
    return StateQueries.getTaskNamesToZones(stateStore);
  }

  /**
   * @see StateQueries
   */
  @Path("/zone/tasks/{taskName}")
  @GET
  public Response getTaskNameToZone(@PathParam("taskName") String taskName) {
    return StateQueries.getTaskNameToZone(stateStore, taskName);
  }

  /**
   * @see StateQueries
   */
  @Path("/zone/{podType}/{ip}")
  @GET
  public Response getTaskIPsToZones(@PathParam("podType") String podType, @PathParam("ip") String ip) {
    return StateQueries.getTaskIPsToZones(stateStore, podType, ip);
  }

  /**
   * @see StateQueries
   */
  @Path("/properties")
  @GET
  public Response getPropertyKeys() {
    return StateQueries.getPropertyKeys(stateStore);
  }

  /**
   * @see StateQueries
   */
  @Path("/properties/{key}")
  @GET
  public Response getProperty(@PathParam("key") String key) {
    return StateQueries.getProperty(stateStore, propertyDeserializer, key);
  }

  /**
   * @see StateQueries
   */
  @Path("/refresh")
  @PUT
  public Response refreshCache() {
    return StateQueries.refreshCache(stateStore);
  }
}
