package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementations for common operations on Task/Executor/Resource requirements.
 */
public class RequirementUtils {

    public static Collection<String> getResourceIds(Collection<ResourceRequirement> resourceRequirements) {
        Collection<String> resourceIds = new ArrayList<>();

        for (ResourceRequirement resReq : resourceRequirements) {
            if (resReq.expectsResource()) {
                resourceIds.add(resReq.getResourceId());
            }
        }

        return resourceIds;
    }

    public static Collection<String> getPersistenceIds(Collection<ResourceRequirement> resourceRequirements) {
        Collection<String> persistenceIds = new ArrayList<String>();

        for (ResourceRequirement resReq : resourceRequirements) {
            String persistenceId = resReq.getPersistenceId();
            if (persistenceId != null) {
                persistenceIds.add(persistenceId);
            }
        }

        return persistenceIds;
    }

    public static Collection<ResourceRequirement> getResourceRequirements(Collection<Resource> resources) {
        Collection<ResourceRequirement> resourceRequirements = new ArrayList<>();

        for (Resource resource : resources) {
            if (!isDynamicPort(resource) && !isNamedVIPPort(resource)) {
                resourceRequirements.add(new ResourceRequirement(resource));
            }
        }

        return resourceRequirements;
    }

    public static Collection<DynamicPortRequirement> getDynamicPortRequirements(List<Resource> resources)
                    throws DynamicPortRequirement.DynamicPortException {
        Collection<DynamicPortRequirement> portRequirements = new ArrayList<>();

        for (Resource resource : resources) {
            if (isDynamicPort(resource)) {
                portRequirements.add(new DynamicPortRequirement(resource));
            }
        }

        return portRequirements;
    }

    public static Collection<NamedVIPPortRequirement> getNamedVIPPortRequirements(List<Resource> resources)
            throws NamedVIPPortRequirement.NamedVIPPortException {
        Collection<NamedVIPPortRequirement> portRequirements = new ArrayList<>();

        for (Resource resource : resources) {
            if (isNamedVIPPort(resource)) {
                portRequirements.add(new NamedVIPPortRequirement(resource));
            }
        }

        return portRequirements;
    }

    static boolean isDynamicPort(Resource resource) {
        if (resource.getName().equals("ports")) {
            List<Protos.Value.Range> ranges = resource.getRanges().getRangeList();
            return ranges.size() == 1 && ranges.get(0).getBegin() == 0 && ranges.get(0).getEnd() == 0;
        }

        return false;
    }

    static boolean isNamedVIPPort(Resource resource) {
        if (isPortRequirement(resource)) {
            return NamedVIPPortRequirement.getVIPLabelKey(resource) != null &&
                    NamedVIPPortRequirement.getVIPLabelValue(resource) != null;
        }

        return false;
    }

    static boolean isPortRequirement(Resource resource) {
        if (resource.getName().equals("ports")) {
            List<Protos.Value.Range> ranges = resource.getRanges().getRangeList();
            return ranges.size() == 1;
        }

        return false;
    }
}
