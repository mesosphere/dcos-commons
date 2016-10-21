package org.apache.mesos.offer.constrain;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Handles the deserialization of registered {@link PlacementRuleGenerator}s.
 */
public class PlacementRuleGeneratorRegistry extends JsonDeserializer<PlacementRuleGenerator> {

    private static final PlacementRuleGeneratorRegistry INSTANCE = new PlacementRuleGeneratorRegistry();

    /**
     * Call {@link #getInstance()}.
     */
    private PlacementRuleGeneratorRegistry() {
    }

    public static PlacementRuleGeneratorRegistry getInstance() {
        return INSTANCE;
    }

    public void register(PlacementRuleGenerator generator) {
        throw new UnsupportedOperationException();
    }

    public PlacementRuleGenerator deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        return null;
    }

    /**
     * Wrapper for the singleton {@link PlacementRuleGeneratorRegistry}.
     */
    public static class Deserializer extends JsonDeserializer<PlacementRuleGenerator> {
        @Override
        public PlacementRuleGenerator deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            return getInstance().deserialize(p, ctxt);
        }
    }
}
