package com.mesosphere.sdk.api.types;

import javax.ws.rs.ext.ContextResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mesosphere.sdk.api.ResponseUtils;
import com.mesosphere.sdk.config.SerializationUtils;
import org.json.JSONArray;

/**
 * Implements pretty-printed/indented JSON output for API Resources.
 *
 * API Resources which return JSON bean objects via
 * {@link ResponseUtils#jsonResponse(JSONArray, javax.ws.rs.core.Response.Status)} should extend this class.
 */
public class PrettyJsonResource implements ContextResolver<ObjectMapper> {

    /**
     * Create a default object mapper (with support for e.g. protobufs), but with pretty formatting enabled:
     */
    private static final ObjectMapper PRETTY_MAPPER = SerializationUtils.registerDefaultModules(new ObjectMapper());
    static {
        PRETTY_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return PRETTY_MAPPER;
    }
}
