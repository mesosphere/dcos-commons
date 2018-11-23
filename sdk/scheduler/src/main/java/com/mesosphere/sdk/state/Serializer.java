package com.mesosphere.sdk.state;

import java.io.IOException;

/**
 * Interface for serializing and deserializing objects to byte arrays using some serialization
 * format.
 */
public interface Serializer {
  <T> byte[] serialize(T value) throws IOException;

  <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException;
}
