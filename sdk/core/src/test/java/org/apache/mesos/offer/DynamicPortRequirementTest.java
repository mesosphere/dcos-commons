package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests DynamicPortRequirements.
 */
public class DynamicPortRequirementTest {

    @Test
    public void testDynamicPortRequirementConstruction() throws DynamicPortRequirement.DynamicPortException {
        Protos.Resource dynPortResource = DynamicPortRequirement.getDesiredDynamicPort(
                TestConstants.PORT_NAME,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        DynamicPortRequirement dynamicPortRequirement = new DynamicPortRequirement(dynPortResource);
        Assert.assertNotNull(dynamicPortRequirement);
    }
}
