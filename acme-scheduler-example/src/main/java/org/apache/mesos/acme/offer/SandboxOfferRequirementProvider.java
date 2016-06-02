package org.apache.mesos.acme.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.acme.state.AcmeStateService;
import org.apache.mesos.offer.OfferRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class SandboxOfferRequirementProvider implements AcmeOfferRequirementProvider {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public SandboxOfferRequirementProvider(AcmeStateService acmeState) {
  }

  @Override
  public OfferRequirement getNewOfferRequirement(String configName, int brokerId) {
    return null;
  }

  @Override
  public OfferRequirement getReplacementOfferRequirement(Protos.TaskInfo taskInfo) {
    return null;
  }

  @Override
  public OfferRequirement getUpdateOfferRequirement(String configName, Protos.TaskInfo info) {
    return null;
  }
}
