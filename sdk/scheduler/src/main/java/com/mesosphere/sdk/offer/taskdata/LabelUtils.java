package com.mesosphere.sdk.offer.taskdata;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.offer.TaskException;

/**
 * Common utility methods for classes which access and manipulate label data.
 */
class LabelUtils {

    private LabelUtils() {
        // do not instantiate
    }

    /**
     * Returns a Map representation of the provided {@link Labels}.
     * In the event of duplicate labels, the last duplicate wins.
     *
     * @see #toProto(Map)
     */
    static Map<String, String> toMap(Labels labels) {
        // sort labels alphabetically for convenience in debugging/logging:
        Map<String, String> map = new TreeMap<>();
        for (Label label : labels.getLabelsList()) {
            map.put(label.getKey(), label.getValue());
        }
        return map;
    }

    /**
     * Returns a Protobuf representation of the provided {@link Map}.
     *
     * @see #toMap(Labels)
     */
    static Labels toProto(Map<String, String> labels) {
        Labels.Builder labelsBuilder = Labels.newBuilder();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            labelsBuilder.addLabelsBuilder()
                .setKey(entry.getKey())
                .setValue(entry.getValue());
        }
        return labelsBuilder.build();
    }

    /**
     * Returns an encoded representation of the provided {@link HealthCheck}, suitable for storing in a Label.
     *
     * @see #decodeHealthCheck(String)
     */
    static String encodeHealthCheck(HealthCheck healthCheck) {
        return new String(Base64.encodeBase64(healthCheck.toByteArray()), StandardCharsets.UTF_8);
    }

    /**
     * Decodes the provided encoded data as a {@link HealthCheck}, or throws {@link TaskException} if decoding failed.
     *
     * @see #encodeHealthCheck(HealthCheck)
     */
    static HealthCheck decodeHealthCheck(String data) throws TaskException {
        byte[] decodedBytes = Base64.decodeBase64(data);
        try {
            return HealthCheck.parseFrom(decodedBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new TaskException(e);
        }
    }
}
