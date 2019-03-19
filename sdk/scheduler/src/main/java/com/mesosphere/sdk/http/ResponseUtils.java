package com.mesosphere.sdk.http;

import com.google.protobuf.Message;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Utilities for building RPC responses.
 */
public final class ResponseUtils {

  private ResponseUtils() {
    // do not instantiate
  }

  /**
   * Returns a 200 OK response containing the provided {@link JSONArray}.
   */
  public static Response jsonOkResponse(JSONArray jsonArray) {
    return jsonResponse(jsonArray, Response.Status.OK);
  }

  /**
   * Returns a 200 OK response containing the provided {@link JSONObject}.
   */
  public static Response jsonOkResponse(JSONObject jsonObject) {
    return jsonResponse(jsonObject, Response.Status.OK);
  }

  /**
   * Returns a 200 OK response containing a JSON representation of the provided protobuf {@link Message}.
   */
  public static Response jsonOkResponse(Message protoMessage) {
    return jsonResponse(protoMessage, Response.Status.OK);
  }

  /**
   * Returns a response containing the provided {@link JSONArray}.
   */
  public static Response jsonResponse(JSONArray jsonArray, Response.Status status) {
    return jsonResponseBean(jsonArray.toString(2), status);
  }

  /**
   * Returns a response containing the provided {@link JSONObject} and {@link Response.Status}.
   */
  public static Response jsonResponse(JSONObject jsonObject, Response.Status status) {
    return jsonResponseBean(jsonObject.toString(2), status);
  }

  /**
   * Returns a response containing a JSON representation of the provided protobuf {@link Message}
   * and {@link Response.Status}.
   */
  public static Response jsonResponse(Message protoMessage, Response.Status status) {
    return jsonResponseBean(protoMessage, status);
  }

  /**
   * Returns a response containing the provided JSON {@link String} and status.
   * <p>
   * Resources which call this directly should extend {@link com.mesosphere.sdk.http.types.PrettyJsonResource}.
   */
  public static Response jsonResponseBean(JSONObject jsonObject, int status) {
    return Response
        .status(status)
        .entity(jsonObject.toString(2))
        .type(MediaType.APPLICATION_JSON_TYPE)
        .build();
  }

  /**
   * Returns a response containing the provided JSON {@link String} and {@link Response.Status}.
   * <p>
   * Resources which call this directly should extend {@link com.mesosphere.sdk.http.types.PrettyJsonResource}.
   */
  public static Response jsonResponseBean(Object entity, Response.Status status) {
    return Response.status(status).entity(entity).type(MediaType.APPLICATION_JSON_TYPE).build();
  }

  /**
   * Returns a 200 OK response containing the provided plaintext {@link String}.
   */
  public static Response plainOkResponse(String plaintext) {
    return plainResponse(plaintext, Response.Status.OK);
  }

  /**
   * Returns a response containing the provided plaintext {@link String} with the
   * provided status {@link Response.Status}.
   */
  public static Response plainResponse(String plaintext, Response.Status status) {
    return Response.status(status).entity(plaintext).type(MediaType.TEXT_PLAIN_TYPE).build();
  }


  /**
   * Returns a response containing the provided plaintext {@link String} with the
   * provided statusCode.
   */
  public static Response plainResponse(String plaintext, int statusCode) {
    return Response.status(statusCode).entity(plaintext).type(MediaType.TEXT_PLAIN_TYPE).build();
  }

  /**
   * Returns a "404 [itemType] not found" response.
   */
  public static Response notFoundResponse(String itemType) {
    return plainResponse(itemType + " not found", Response.Status.NOT_FOUND);
  }

  /**
   * Returns a "404 Run [serviceName] not found" response.
   */
  public static Response serviceNotFoundResponse(String serviceName) {
    return notFoundResponse("Service " + serviceName);
  }

  /**
   * Returns a "208 Already reported" response.
   */
  public static Response alreadyReportedResponse() {
    // SUPPRESS CHECKSTYLE MagicNumber
    return plainResponse("Command has already been reported or completed", 208);
  }

  /**
   * Returns a 200 OK response containing the provided html {@Link String}.
   */
  public static Response htmlOkResponse(String html) {
    return htmlResponse(html, Response.Status.OK);
  }

  /**
   * Returns a response containing the provided html {@Link String} with the provided status {@Link Response.Status}.
   */
  public static Response htmlResponse(String html, Response.Status status) {
    return Response.status(status).entity(html).type(MediaType.TEXT_HTML).build();
  }
}
