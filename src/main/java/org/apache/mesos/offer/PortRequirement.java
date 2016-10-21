package org.apache.mesos.offer;

import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Optional;
import java.util.List;


/**
 * This class allows the expression of a desire for a random port designated by a name which will be injected into the
 * environment.
 */
public class PortRequirement extends ResourceRequirement {
    private static final String PORT_KEY = "envName_port";
    private String envName;   // if there is name, then
    private int port=0;
    Optional<String> vipNameOptional;
    Optional<int> vipPortOptional;

    public PortRequirement(Protos.Resource resource) throws Exception {
        super(resource);
        this.envName = getEnvName(resource);
        validate();
    }

    public String getEnvName() {
        return envName;
    }

    public static Protos.Resource getDesiredPort(String envName, int portNumber, String role, String principal) {
        Protos.Resource resource= ResourceUtils.getDesiredRanges(
                        role,
                        principal,
                        "ports",
                        Arrays.asList(
                                Protos.Value.Range.newBuilder()
                                .setBegin(portNumber)
                                .setEnd(portNumber)
                                .build())
                        );
        if (envName != null) {
            Protos.Label label = Protos.Labels.newBuilder()
                    .addLabelsBuilder()
                    .setKey(PortRequirement.PORT_KEY)
                    .setValue(envName).build();

            resource = Protos.Resource.newBuilder(resource)
                    .setReservation(Protos.Resource.ReservationInfo.newBuilder(resource.getReservation())
                            .clearLabels()
                            .setLabels(labels))
                    .build();
        }
        return resource;
    }


    //why do you have getEnvironment -  why it is only for portName !!!
    public static Protos.Environment updateEnvironment(Protos.Environment env, List<Protos.Resource> resources) {
        Protos.Environment.Builder envBuilder = Protos.Environment.newBuilder();
        for (Protos.Resource resource : resources) {
            String envName = PortRequirement.getEnvName(resource);
            if (envName != null) {
                String portNumber = String.valueOf(resource.getRanges().getRange(0).getBegin());
                envBuilder.addVariables(Protos.Environment.Variable.newBuilder()
                        .setName(portName)
                        .setValue(portNumber));
            }
        }
        return Protos.Environment.newBuilder(env)
                .addAllVariables(envBuilder.build().getVariablesList())
                .build();
    }

    private static String getEnvName(Protos.Resource resource) {
        if ( resource.hasReservation() && resource.getReservation().hasLabels() ) {
            for (Protos.Label label : resource.getReservation().getLabels().getLabelsList()) {
                if (label.getKey().equals(PORT_KEY)) {
                    return label.getValue();
                }
            }
        }
        return null;
    }

    private void validate() throws PortException {
        if (envName == null && vipNameOptional.isPresent()) {
            throw new PortException("if I do not have a name but already have a VIP label, how can I match them");
        }
        if (! (getResource().getName().equals("ports")) {
            throw new PortException("PortRequirement shoud have a resource with name \"ports\"");
        }
        List<Protos.Value.Range> ranges = getResource().getRanges().getRangeList();
        if ( ! ( ranges.size() == 1 && ranges.get(0).getBegin() == ranges.get(0).getEnd()  ) ) {
            throw new PortException("Resource should be a single valued range of zero, is: " + getResource());
        }
    }

    /**
     * Exception thrown when attempting to create malformed PortRequirements.
     */
    public static class PortException extends InvalidRequirementException {
        public PortException(String msg) {
            super(msg);
        }

        public PortException(Exception e) {
            super(e);
        }
    }
}
