package com.mesosphere.sdk.offer.taskdata;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.mesos.Protos.HealthCheck;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.offer.TaskException;

/**
 * Common utility methods for encoding/decoding labels for old Custom Executors.
 */
public class ExecutorLabelUtils {

    private ExecutorLabelUtils() {
        // do not instantiate
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
