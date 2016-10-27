package org.apache.mesos.offer;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Label;

import java.util.List;
import java.util.Optional;

/**
 * A ResourceRequirement encapsulate a needed Resource.
 * A Resource object indicates whether it is expected to exist already or
 * to be created by the presence or absence of IDs respectively.  A Resource
 * indicates that is expected to exist by having a Label with the key
 * "resource_id" attached to it.  A Volume simliarly indicates the same need
 * for creation or expected existence by having a persistence ID of an empty string
 * or an already determined value.
 */
public class ResourceRequirement {
    /* keys for log */
    public static final String ENV_KEY = "Environment_Name";
    public static final String VIP_KEY = "VIP_KEY";
    public static final String VIP_VALUE = "VIP_VALUE";

    private MesosResource mesosResource;
    private DiskInfo diskInfo;

    private Optional<String> envName = Optional.empty();
    private Optional<Label> vipLabel = Optional.empty();

    public ResourceRequirement(Resource resource) {
        this.mesosResource = new MesosResource(resource);
        this.diskInfo = getDiskInfo();
        this.envName = Optional.ofNullable(ResourceUtils.setEnvName(resource));
        this.vipLabel = Optional.ofNullable(ResourceUtils.setVIPLabel(resource));
    }

    public boolean hasEnvName() {
        if (envName.isPresent()) {
            return true;
        }
        return false;
    }

    public boolean hasVIPLabel() {

        return vipLabel.isPresent();
    }


    public String getEnvName() {
        if (hasEnvName()) {
            return envName.get();
        }
        return null;
    }

    public Label getVIPLabel() {
        if (hasVIPLabel()) {
            return vipLabel.get();
        }
        return null;
    }


    public static ResourceRequirement setEnvName(ResourceRequirement resReq, String envName) {
        if (envName != null && envName.length() > 0) {
            return new ResourceRequirement(
                    ResourceUtils.getResourceAddLabelUnique(resReq.getResource(),
                            Label.newBuilder()
                                    .setKey(ENV_KEY)
                                    .setValue(envName)
                                    .build()));
        }
        return resReq;
    }

    public static ResourceRequirement setVIPLabel(ResourceRequirement resReq, Label label) {
        if (label != null) {
            return new ResourceRequirement(
                    ResourceUtils.getResourceAddLabelUnique(
                            ResourceUtils.getResourceAddLabelUnique(resReq.getResource(),
                                    Label.newBuilder()
                                            .setKey(VIP_KEY)
                                            .setValue(label.getKey())
                                            .build()),
                            Label.newBuilder()
                                    .setKey(VIP_VALUE)
                                    .setValue(label.getValue())
                                    .build()));
        }
        return resReq;
    }

    public boolean isDynamicPort() {
        if (getResource().getName().equals("ports")) {
            List<Protos.Value.Range> ranges = getResource().getRanges().getRangeList();
            return ranges.size() == 1 && ranges.get(0).getBegin() == 0 && ranges.get(0).getEnd() == 0;
        }
        return false;
    }

    /**
     * If resource is "ports" and if the current value is set to 0. Otherwise does not update
     */
    public static ResourceRequirement addPort(ResourceRequirement resReq, int port) {
        if (resReq.isDynamicPort()) {
            Value.Range range = Value.Range.newBuilder().setBegin(port).setEnd(port).build();
            Resource resource = Resource.newBuilder(resReq.getResource()).
                    setRanges(Value.Ranges.newBuilder().addRange(range).build()).build();
            return new ResourceRequirement(resource);
        }
        return resReq;
    }

    public Resource getResource() {
        return mesosResource.getResource();
    }

    public String getRole() {
        return mesosResource.getRole();
    }

    public String getPrincipal() {
        return mesosResource.getPrincipal();
    }

    public String getName() {
        return mesosResource.getName();
    }

    public String getResourceId() {
        return mesosResource.getResourceId();
    }

    public String getPersistenceId() {
        if (hasPersistenceId()) {
            return diskInfo.getPersistence().getId();
        } else {
            return null;
        }
    }

    public boolean consumesUnreservedResource() {
        return !expectsResource() && !reservesResource();
    }

    public boolean reservesResource() {
        if (!hasReservation()) {
            return false;
        }
        return !expectsResource();
    }

    public boolean expectsResource() {
        if (hasResourceId() && !getResourceId().isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    public boolean expectsVolume() {
        if (!hasPersistenceId()) {
            return false;
        }

        String persistenceId = getPersistenceId();

        if (persistenceId.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public boolean createsVolume() {
        if (!hasPersistenceId()) {
            return false;
        }

        String persistenceId = getPersistenceId();

        if (persistenceId.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    public boolean needsVolume() {
        return expectsVolume() || createsVolume();
    }

    public boolean isAtomic() {
        return mesosResource.isAtomic();
    }

    public Value getValue() {
        return mesosResource.getValue();
    }

    public String getEnvValue() {
        Value value = getValue();
        if (value.getType() == Value.Type.RANGES && value.getRanges().getRangeCount() == 1) {
            return Long.toString(value.getRanges().getRange(0).getBegin());
        }
        if (value.getType() == Value.Type.SCALAR) {
            return value.getScalar().toString();
        }
        if (value.getType() == Value.Type.SET && value.getSet().getItemCount() == 1) {
            return value.getSet().getItem(0);
        }
        throw new IllegalArgumentException("Expecting a single value : " + value.getType() +
                " value : " + value.toString());
    }

    private boolean hasResourceId() {
        return mesosResource.hasResourceId();
    }

    private boolean hasPersistenceId() {
        if (!hasDiskInfo()) {
            return false;
        }

        return diskInfo.hasPersistence();
    }

    private boolean hasReservation() {
        return mesosResource.hasReservation();
    }

    private boolean hasDiskInfo() {
        return mesosResource.getResource().hasDisk();
    }

    private DiskInfo getDiskInfo() {
        if (hasDiskInfo()) {
            return mesosResource.getResource().getDisk();
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
