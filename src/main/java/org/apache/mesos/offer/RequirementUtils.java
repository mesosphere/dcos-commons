package org.apache.mesos.offer;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.mesos.Protos.Resource;

/**
 * Implementations for common operations on Task/Executor/Resource requirements.
 */
public class RequirementUtils {

  public static Collection<String> getResourceIds(Collection<ResourceRequirement> resourceRequirements) {
    Collection<String> resourceIds = new ArrayList<String>();

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
    Collection<ResourceRequirement> resourceRequirements = new ArrayList<ResourceRequirement>();

    for (Resource resource : resources) {
      resourceRequirements.add(new ResourceRequirement(resource));
    }

    return resourceRequirements;
  }

}
