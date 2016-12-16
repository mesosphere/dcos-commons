/*
 * Copyright 2016 Mesosphere
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mesosphere.sdk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;

import java.io.IOException;

/**
 * Contains static object serialization utilities for JSON and YAML.
 */
public class SerializationUtils {

    private SerializationUtils() {
        // do not instantiate
    }

    /**
     * An Object mapper that can be used for mapping Objects to and from YAML. Includes support for
     * serializing/deserializing Protobuf objects.
     */
    private static final ObjectMapper DEFAULT_YAML_MAPPER = registerDefaultModules(new ObjectMapper(new YAMLFactory()));

    /**
     * An Object mapper that can be used for mapping Objects to and from JSON. Includes support for
     * serializing/deserializing Protobuf objects.
     */
    private static final ObjectMapper DEFAULT_JSON_MAPPER = registerDefaultModules(new ObjectMapper());

    /**
     * Returns a new {@link ObjectMapper} with default modules against the provided factory.
     *
     * @param mapper the instance to register default modules with
     */
    public static ObjectMapper registerDefaultModules(ObjectMapper mapper) {
        // enable support for ...
        return mapper.registerModules(
                new GuavaModule(),     // Guava types
                new JavaTimeModule(),  // java.time.* types
                new Jdk8Module(),      // Optional<>s
                new ProtobufModule()); // Protobuf objects
    }

    /**
     * Returns a YAML representation of the provided value.
     *
     * @param value The value that will be converted to YAML
     * @param <T> The type of the {@code value}
     * @return A YAML representation of the {@code value}
     * @throws IOException if conversion fails
     */
    public static <T> String toYamlString(T value) throws IOException {
        return toString(value, DEFAULT_YAML_MAPPER);
    }

    /**
     * Returns a JSON representation of the provided value, or an empty string if conversion fails.
     * This is a convenience function for cases like {@link Object#toString()}.
     *
     * @param value The value that will be converted to JSON
     * @param <T> The type of the {@code value}
     * @return A JSON representation of the {@code value}, or an empty string if conversion fails
     */
    public static <T> String toYamlStringOrEmpty(T value) {
        try {
            return toYamlString(value);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Returns the object represented by the provided YAML string created via
     * {@link #toYamlString(Object)}.
     */
    public static <T> T fromYamlString(String str, Class<T> clazz) throws IOException {
        return fromString(str, clazz, DEFAULT_YAML_MAPPER);
    }

    /**
     * Returns a JSON representation of the provided value.
     *
     * @param value The value that will be converted to JSON
     * @param <T> The type of the {@code value}
     * @return A JSON representation of the {@code value}
     * @throws IOException if conversion fails
     */
    public static <T> String toJsonString(T value) throws IOException {
        return toString(value, DEFAULT_JSON_MAPPER);
    }

    /**
     * Returns a JSON representation of the provided value, or an empty string if conversion fails.
     * This is a convenience function for cases like {@link Object#toString()}.
     *
     * @param value The value that will be converted to JSON
     * @param <T> The type of the {@code value}
     * @return A JSON representation of the {@code value}, or an empty string if conversion fails
     */
    public static <T> String toJsonStringOrEmpty(T value) {
        try {
            return toJsonString(value);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Returns the object represented by the provided JSON string created via
     * {@link #toJsonString(Object)}.
     */
    public static <T> T fromJsonString(String str, Class<T> clazz) throws IOException {
        return fromString(str, clazz, DEFAULT_JSON_MAPPER);
    }

    /**
     * Returns a representation of the provided value using the provided custom object mapper.
     */
    public static <T> String toString(T value, ObjectMapper mapper) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    /**
     * Returns a representation of the provided value using the provided custom object mapper.
     */
    public static <T> T fromString(String str, Class<T> clazz, ObjectMapper mapper) throws IOException {
        return mapper.readValue(str, clazz);
    }
}
