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
}
