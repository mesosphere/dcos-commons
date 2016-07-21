package org.apache.mesos.state.api;

/**
 * Interface which handles deserializing StateStore Property data for viewing by users.
 *
 * @see com.googlecode.protobuf.format.JsonFormat for turning protobufs into JSON
 */
public interface PropertyDeserializer {
    /**
     * Returns a valid JSON representation for the data in {@code value} for display to an end user.
     * {@code key} is provided to support datatypes which vary on a per-key basis.
     */
    public String toJsonString(String key, byte[] value);
}
