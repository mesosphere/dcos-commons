package com.mesosphere.sdk.offer.taskdata;

import java.util.Map;
import java.util.TreeMap;

import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;

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
}
