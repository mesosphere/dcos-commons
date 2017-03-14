package com.mesosphere.sdk.kafka.upgrade.old;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;


/* copy from dcos-kafka-service */

/**
 * HeapConfig contains the configuration for the JVM heap for a Kafka
 * broker.
 */
public class HeapConfig {
    @JsonProperty("size_mb")
    private int sizeMb;

    public HeapConfig() {

    }

    @JsonCreator
    public HeapConfig(final int sizeMb) {
        this.sizeMb = sizeMb;
    }

    public int getSizeMb() {
        return sizeMb;
    }

    @JsonProperty("size_mb")
    public void setSizeMb(int sizeMb) {
        this.sizeMb = sizeMb;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HeapConfig that = (HeapConfig) o;
        return sizeMb == that.sizeMb;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sizeMb);
    }

    @Override
    public String toString() {
        return "HeapConfig{" +
                "sizeMb=" + sizeMb +
                '}';
    }
}
