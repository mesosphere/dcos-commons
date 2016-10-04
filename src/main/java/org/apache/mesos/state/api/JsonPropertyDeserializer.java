package org.apache.mesos.state.api;

import org.apache.mesos.state.StateStoreException;

import java.nio.charset.Charset;

/**
 * Converts JSON byte arrays to JSON Strings.
 */
public class JsonPropertyDeserializer implements PropertyDeserializer {
    private static final Charset DEFAULT_CHAR_SET = Charset.forName("UTF-8");
    private final Charset charset;

    public JsonPropertyDeserializer() {
        this(DEFAULT_CHAR_SET);
    }

    public JsonPropertyDeserializer(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String toJsonString(String key, byte[] value) throws StateStoreException {
        return new String(value, charset);
    }
}
