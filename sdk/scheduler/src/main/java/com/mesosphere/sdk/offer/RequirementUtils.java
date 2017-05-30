package com.mesosphere.sdk.offer;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Implementations for common operations on Task/Executor/Resource requirements.
 */
public class RequirementUtils {

    public static Collection<String> getResourceIds(Collection<ResourceRequirement> resourceRequirements) {
        Collection<String> resourceIds = new ArrayList<>();

        for (ResourceRequirement resReq : resourceRequirements) {
            if (resReq.expectsResource()) {
                resourceIds.add(resReq.getResourceId().get());
            }
        }

        return resourceIds;
    }

    public static Collection<String> getPersistenceIds(Collection<ResourceRequirement> resourceRequirements) {
        Collection<String> persistenceIds = new ArrayList<>();

        for (ResourceRequirement resReq : resourceRequirements) {
            String persistenceId = resReq.getPersistenceId();
            if (persistenceId != null) {
                persistenceIds.add(persistenceId);
            }
        }

        return persistenceIds;
    }
}
