package org.apache.mesos.offer;

import jdk.nashorn.internal.ir.Labels;
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

    /* keep those in Labels */
    private Optional<String> envName;
    private Optional<Label> vipLabel;

    public ResourceRequirement(Resource resource) {
        this.mesosResource = new MesosResource(resource);
        this.diskInfo = getDiskInfo();
        ResourceUtils.setEnvName(this);
        ResourceUtils.setVIPLabel(this);

    }

    public boolean hasEnvName() {
        return envName.isPresent();
    }

    public String getEnvName() {
        if (hasEnvName()) return envName.get();
        return null;
    }


    /* set envName - also add to the Label */
    public void setEnvName(String envName) {

        this.envName = Optional.of(envName);
        this.mesosResource = new MesosResource(
                ResourceUtils.getResourceAddLabel(getResource(),
                        Label.newBuilder()
                                .setKey(ENV_KEY)
                                .setValue(getEnvName())
                                .build()));
     /*   Protos.Labels.Builder labelBuilder = Protos.Labels.newBuilder(resource.getReservation().getLabels());
        labelBuilder.addLabelsBuilder()
                .setKey(ENV_KEY)
                .setValue(envName)
                .build();
        this.mesosResource=new MesosResource(
                Resource.newBuilder(resource)
                    .setReservation(
                        Resource.ReservationInfo.newBuilder(resource.getReservation())
                                .clearLabels()
                                .setLabels(labelBuilder.build()))
                     .build()
        );
        */
    }


    /* set vip_ket and vip value  - also add to the Label */
    public void setVIPLabel(Label label){

        this.vipLabel=Optional.of(label);
        this.mesosResource=new MesosResource(
                ResourceUtils.getResourceAddLabel(this.getResource(),
                        Label.newBuilder()
                                .setKey(ENV_KEY)
                                .setValue(this.vipLabel.get().getKey())
                                .build() ));
        this.mesosResource=new MesosResource(
                ResourceUtils.getResourceAddLabel(this.getResource(),
                        Label.newBuilder()
                                .setKey(VIP_VALUE)
                                .setValue(this.vipLabel.get().getValue())
                                .build() ));
/*
        Resource resource=getResource();
        Protos.Labels.Builder labelBuilder = Protos.Labels.newBuilder(resource.getReservation().getLabels());
        labelBuilder.addLabelsBuilder()
                .setKey(VIP_KEY)
                .setValue(this.vipLabel.get().getKey());
        labelBuilder.addLabelsBuilder()
                .setKey(VIP_VALUE)
                .setValue(this.vipLabel.get().getValue())
                .build();
        this.mesosResource=new MesosResource(
                Resource.newBuilder(resource)
                        .setReservation(
                                Resource.ReservationInfo.newBuilder(resource.getReservation())
                                        .clearLabels()
                                        .setLabels(labelBuilder.build()))
                        .build()
        );
        */
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

    /*    returns value for envName=<value> */
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
        throw new IllegalArgumentException("Expecting a single value : " + value.getType() + " value : " + value.toString());
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
