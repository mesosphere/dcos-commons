package com.mesosphere.sdk.api.types;

import com.mesosphere.sdk.state.StateStoreException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of {@link PropertyDeserializer} which converts UTF-8 JSON byte arrays to Strings.
 */
public class StringPropertyDeserializer implements PropertyDeserializer {
    private static final Charset DEFAULT_CHAR_SET = StandardCharsets.UTF_8;
    private final Charset charset;

    public StringPropertyDeserializer() {
        this(DEFAULT_CHAR_SET);
    }

    public StringPropertyDeserializer(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String toJsonString(String key, byte[] value) throws StateStoreException {
        return new String(value, charset);
    }
}
