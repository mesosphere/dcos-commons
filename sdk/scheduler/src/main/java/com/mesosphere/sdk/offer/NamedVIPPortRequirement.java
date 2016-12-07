package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;

import java.util.Arrays;

/**
 * This class describes port requirements that should be routed to with a named VIP (or "service address") in DC/OS.
 */
public class NamedVIPPortRequirement extends ResourceRequirement {
    private static final String VIP_LABEL_NAME_KEY = "vip_key";
    private static final String VIP_LABEL_VALUE_KEY = "vip_value";
    private String vipKey;
    private String vipValue;

    public NamedVIPPortRequirement(Protos.Resource resource) throws NamedVIPPortException {
        super(resource);
        this.vipKey = getVIPLabelKey(resource);
        this.vipValue = getVIPLabelValue(resource);
        validate();
    }

    public static String getVIPLabelKey(Protos.Resource resource) {
        return getLabelValue(VIP_LABEL_NAME_KEY, resource);
    }

    public static String getVIPLabelValue(Protos.Resource resource) {
        return getLabelValue(VIP_LABEL_VALUE_KEY, resource);
    }

    private static String getLabelValue(String key, Protos.Resource resource) {
        if (!resource.hasReservation()) {
            return null;
        }

        if (!resource.getReservation().hasLabels()) {
            return null;
        }

        for (Protos.Label label : resource.getReservation().getLabels().getLabelsList()) {
            if (label.getKey().equals(key)) {
                return label.getValue();
            }
        }

        return null;
    }

    public static Protos.Resource getDesiredNamedVIPPort(
            String key, String name, Long port, String role, String principal) {
        return ResourceUtils.setVIPPortName(
                ResourceUtils.getDesiredRanges(
                        role,
                        principal,
                        "ports",
                        Arrays.asList(
                                Protos.Value.Range.newBuilder()
                                .setBegin(port)
                                .setEnd(port)
                                .build()
                        )
                ),
                key,
                name
        );
    }

    private void validate() throws NamedVIPPortException {
        if (vipKey == null) {
            throw new NamedVIPPortException("VIP label key is null");
        }

        if (vipValue == null) {
            throw new NamedVIPPortException("VIP service address is null");
        }

        if (!RequirementUtils.isPortRequirement(getResource())) {
            throw new NamedVIPPortException(
                    "Resource should be a port and a single valued range, is: " + getResource());
        }
    }

    /**
     * Exception thrown when attempting to create malformed NamedVIPPortRequirements.
     */
    public static class NamedVIPPortException extends InvalidRequirementException {

        public NamedVIPPortException(String msg) {
            super(msg);
        }
    }
}
