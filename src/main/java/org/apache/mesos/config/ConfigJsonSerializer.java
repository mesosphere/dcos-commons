package org.apache.mesos.config;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.mesos.stream.JsonSerializer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class that converts configuration maps to bytes for storage in zk
 * and converts bytes into configuration maps.
 */
public class ConfigJsonSerializer {

  JsonSerializer serializer = new JsonSerializer();

  public byte[] serialize(Object obj) throws IOException {
    String strObject = serializer.serialize(obj);
    return strObject.getBytes(Charset.defaultCharset());
  }

  @SuppressWarnings("unchecked")
  public Map<String, Map<String, ConfigProperty>> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {

    String json = new String(bytes, Charset.defaultCharset());

    TypeReference<HashMap<String, HashMap<String, ConfigProperty>>> typeRef =
      new TypeReference<HashMap<String, HashMap<String, ConfigProperty>>>() {
      };

    return (Map<String, Map<String, ConfigProperty>>) serializer.deserialize(json, typeRef);
  }
}
