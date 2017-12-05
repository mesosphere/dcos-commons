package com.mesosphere.sdk.api;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.protobuf.Message;

/**
 * Utilities for building RPC responses.
 */
public class ResponseUtils {

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
     * Returns a response containing the provided {@link JSONArray}.
     */
    public static Response jsonResponse(JSONArray jsonArray, Response.Status status) {
        return jsonResponseBean(jsonArray.toString(2), status);
    }

    /**
     * Returns a 200 OK response containing the provided {@link JSONObject}.
     */
    public static Response jsonOkResponse(JSONObject jsonObject) {
        return jsonResponse(jsonObject, Response.Status.OK);
    }

    /**
     * Returns a 200 OK response containing the provided {@link JSONObject}.
     */
    public static Response jsonResponse(JSONObject jsonObject, Response.Status status) {
        return jsonResponseBean(jsonObject.toString(2), status);
    }

    /**
     * Returns a 200 OK response containing a JSON representation of the provided protobuf {@link Message}.
     */
    public static Response jsonOkResponse(Message protoMessage) {
        return jsonResponse(protoMessage, Response.Status.OK);
    }

    /**
     * Returns a 200 OK response containing a JSON representation of the provided protobuf {@link Message}.
     */
    public static Response jsonResponse(Message protoMessage, Response.Status status) {
        return jsonResponseBean(protoMessage, status);
    }

    /**
     * Returns a 200 OK response containing the provided JSON {@link String}.
     *
     * Resources which call this directly should extend {@link com.mesosphere.sdk.api.types.PrettyJsonResource}.
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
     * Returns a "404 Element not found" response.
     */
    public static Response elementNotFoundResponse() {
        return plainResponse("Element not found", Response.Status.NOT_FOUND);
    }

    /**
     * Returns a "208 Already reported" response.
     */
    public static Response alreadyReportedResponse() {
        return plainResponse("Command has already been reported or completed", 208);
    }
}
