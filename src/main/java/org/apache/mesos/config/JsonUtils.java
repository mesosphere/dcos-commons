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
package org.apache.mesos.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

/**
 * Contains static Json manipulation utilities.
 */
public class JsonUtils {

    private JsonUtils(){}

    /**
     * An Object mapper that can be used for mapping Objects to and from YAML.
     */
    public static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory()).registerModule(new GuavaModule())
            .registerModule(new Jdk8Module());

    /**
     * An Object mapper that can be used for mapping Objects to and from JSON.
     */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new GuavaModule())
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module());

    /**
     * Gets a JSON representation of value.
     * @param value The value that will be converted to JSON.
     * @param <T> The type of value.
     * @return A JSON representation of value or the empty string if JSON
     * conversion fails.
     */
    public static <T> String toJsonString(T value){
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public static <T> T fromJsonString(String str, Class<T> clazz) throws IOException {
        return MAPPER.readValue(str, clazz);
    }
}
