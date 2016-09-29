package org.apache.mesos.offer;

import org.apache.mesos.Protos;

import java.util.Arrays;

/**
 * This class allows the expression of a desire for a random port designated by a name which will be injected into the
 * environment.
 */
public class DynamicPortRequirement extends ResourceRequirement {
    private static final String DYNAMIC_PORT_KEY = "dynamic_port";
    private String name;

    public DynamicPortRequirement(Protos.Resource resource) throws DynamicPortException {
        super(resource);
        this.name = getPortName(resource);
        validate();
    }

    public String getPortName() {
        return name;
    }

    public static Protos.Resource getDesiredDynamicPort(String name, String role, String principal) {
        return ResourceUtils.setDynamicPortName(
                ResourceUtils.getDesiredRanges(
                        role,
                        principal,
                        "ports",
                        Arrays.asList(
                                Protos.Value.Range.newBuilder()
                                .setBegin(0)
                                .setEnd(0)
                                .build())
                        ),
                name);
    }

    public static String getPortName(Protos.Resource resource) {
        if (!resource.hasReservation()) {
            return null;
        }

        if (!resource.getReservation().hasLabels()) {
            return null;
        }

        for (Protos.Label label : resource.getReservation().getLabels().getLabelsList()) {
            if (label.getKey().equals(DYNAMIC_PORT_KEY)) {
                return label.getValue();
            }
        }

        return null;
    }

    private void validate() throws DynamicPortException {
        if (name == null) {
            throw new DynamicPortException("Port name is null");
        }

        if (!RequirementUtils.isDynamicPort(getResource())) {
            throw new DynamicPortException("Resource should be a single valued range of zero, is: " + getResource());
        }
    }

    /**
     * Exception thrown when attempting to create malformed DynamicPortRequirements.
     */
    public static class DynamicPortException extends InvalidRequirementException {
        public DynamicPortException(String msg) {
            super(msg);
        }

        public DynamicPortException(Exception e) {
            super(e);
        }
    }
}
