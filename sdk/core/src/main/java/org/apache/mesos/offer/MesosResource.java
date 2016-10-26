package org.apache.mesos.offer;

import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Value;

/**
 * A representation of a Mesos Resources.
 **/
public class MesosResource {
  public static final String RESOURCE_ID_KEY = "resource_id";
  public static final String DYNAMIC_PORT_KEY = "dynamic_port";

  private Resource resource;
  private String resourceId;

  public MesosResource(Resource resource) {
    this.resource = resource;
    this.resourceId = getResourceIdInternal();
  }

  public Resource getResource() {
    return resource;
  }

  public boolean isAtomic() {
    return resource.hasDisk()
      && resource.getDisk().hasSource()
      && resource.getDisk().getSource().getType().equals(Source.Type.MOUNT);
  }

  public String getName() {
    return resource.getName();
  }

  public Value.Type getType() {
    return resource.getType();
  }

  public boolean hasResourceId() {
    return resourceId != null;
  }

  public String getResourceId() {
    return resourceId;
  }

  public boolean hasReservation() {
    return resource.hasReservation();
  }

  public Value getValue() {
    return ValueUtils.getValue(resource);
  }

  public String getRole() {
    return resource.getRole();
  }

  public String getPrincipal() {
    if (hasReservation() &&
        resource.getReservation().hasPrincipal()) {
      return resource.getReservation().getPrincipal();
    }

    return null;
  }

  private String getResourceIdInternal() {
    if (resource.hasReservation()) {
      Labels labels = resource.getReservation().getLabels();

      for (Label label : labels.getLabelsList()) {
        if (label.getKey().equals(RESOURCE_ID_KEY)) {
          return label.getValue();
        }
      }
    }

    return null;
  }
}
