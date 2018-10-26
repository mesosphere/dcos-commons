package com.mesosphere.sdk.http.types;

/**
 * Interface for an object which returns the String content of an endpoint when requested.
 */
public interface EndpointProducer {

  /**
   * Creates and returns an {@link EndpointProducer} which only produces the provided constant value.
   */
  static EndpointProducer constant(final String value) {
    return () -> value;
  }

  /**
   * Returns some endpoint content to be sent in response to an 'endpoints' lookup request. This
   * could be a URL, or the content of a file, etc.
   */
  String getEndpoint();
}
