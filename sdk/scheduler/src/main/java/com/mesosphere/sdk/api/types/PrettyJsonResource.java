package com.mesosphere.sdk.api.types;

import javax.ws.rs.ext.ContextResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Implements pretty-printed/indented JSON output for API Resources.
 *
 * API Resources which return JSON bean objects should extend this class.
 * If you're using {@code ResponseUtils.jsonOkResponse()} then this isn't necessary.
 */
public class PrettyJsonResource implements ContextResolver<ObjectMapper> {

    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper();
    static {
        PRETTY_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return PRETTY_MAPPER;
    }
}
