package com.mesosphere.sdk.api.types;

/**
 * Interface for an object which returns the String content of an endpoint when requested.
 */
public interface EndpointProducer {

    /**
     * Returns some endpoint content to be sent in response to an 'endpoints' lookup request. This
     * could be a URL, or the content of a file, etc.
     */
    public String getEndpoint();

    /**
     * Creates and returns an {@link EndpointProducer} which only produces the provided constant value.
     */
    public static EndpointProducer constant(final String value) {
        return new EndpointProducer() {
            @Override
            public String getEndpoint() {
                return value;
            }
        };
    }
}
