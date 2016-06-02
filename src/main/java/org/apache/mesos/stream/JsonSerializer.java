package org.apache.mesos.stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import java.io.IOException;

/**
 * Serializes and Deserializes from Object to Json and back again.
 */
public class JsonSerializer {

  private Gson gson = new Gson();
  private ObjectMapper mapper;

  public JsonSerializer() {
    JsonFactory factory = new JsonFactory();
    mapper = new ObjectMapper(factory);
  }

  public <T> String serialize(T obj) throws IOException {
    return gson.toJson(obj);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public <T> T deserialize(String jsonString, Class clazz) throws IOException, ClassNotFoundException {
    return (T) gson.fromJson(jsonString, clazz);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public <T> T deserialize(String jsonString, TypeReference typeRef) throws IOException, ClassNotFoundException {
    return (T) mapper.readValue(jsonString, typeRef);
  }
}
