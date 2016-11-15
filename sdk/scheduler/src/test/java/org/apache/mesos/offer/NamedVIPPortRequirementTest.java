package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

public class NamedVIPPortRequirementTest {

    @Test
    public void testNamedVIPPortRequirementConstruction() throws NamedVIPPortRequirement.NamedVIPPortException {
        Protos.Resource vipPortResource = NamedVIPPortRequirement.getDesiredNamedVIPPort(
                TestConstants.VIP_KEY,
                TestConstants.VIP_NAME,
                (long) 10000,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        NamedVIPPortRequirement namedVIPPortRequirement = new NamedVIPPortRequirement(vipPortResource);
        Assert.assertNotNull(namedVIPPortRequirement);
    }

    @Test(expected=NamedVIPPortRequirement.NamedVIPPortException.class)
    public void testNamedVIPPortRequirementCreationFailureOnNullVIPKey()
            throws NamedVIPPortRequirement.NamedVIPPortException {
        Protos.Resource vipPortResource = NamedVIPPortRequirement.getDesiredNamedVIPPort(
                TestConstants.VIP_KEY,
                TestConstants.VIP_NAME,
                (long) 10000,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        Protos.Resource resource = removeLabel(vipPortResource, "vip_key");

        new NamedVIPPortRequirement(resource);
    }

    @Test(expected=NamedVIPPortRequirement.NamedVIPPortException.class)
    public void testNamedVIPPortRequirementCreationFailureOnNullVIPValue()
            throws NamedVIPPortRequirement.NamedVIPPortException {
        Protos.Resource vipPortResource = NamedVIPPortRequirement.getDesiredNamedVIPPort(
                TestConstants.VIP_KEY,
                TestConstants.VIP_NAME,
                (long) 10000,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        Protos.Resource resource = removeLabel(vipPortResource, "vip_value");

        new NamedVIPPortRequirement(resource);
    }

    @Test(expected=NamedVIPPortRequirement.NamedVIPPortException.class)
    public void testNamedVIPPortRequirementCreationFailureOnBadResourceType()
            throws NamedVIPPortRequirement.NamedVIPPortException {
        Protos.Resource vipPortResource = NamedVIPPortRequirement.getDesiredNamedVIPPort(
                TestConstants.VIP_KEY,
                TestConstants.VIP_NAME,
                (long) 10000,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        Protos.Resource resource = Protos.Resource.newBuilder(vipPortResource).setName("not_a_resource").build();

        new NamedVIPPortRequirement(resource);
    }

    private static Protos.Resource removeLabel(Protos.Resource resource, String label) {
        Protos.Labels.Builder labelBuilder = Protos.Labels.newBuilder();
        for (Protos.Label l : labelBuilder.getLabelsList()) {
            if (!l.getKey().equals(label)) {
                labelBuilder.addLabels(l);
            }
        }
        return Protos.Resource.newBuilder(resource)
                .setReservation(Protos.Resource.ReservationInfo.newBuilder(resource.getReservation())
                        .setLabels(labelBuilder)).build();
    }
}
