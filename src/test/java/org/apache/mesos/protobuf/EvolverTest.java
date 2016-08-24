package org.apache.mesos.protobuf;

import org.apache.mesos.Protos.FrameworkInfo;

import org.testng.annotations.Test;

import static org.junit.Assert.assertEquals;


public class EvolverTest {
    private final static String user = "test-user";
    private final static String name = "test-framework";

    @Test
    public void testFrameworkInfo() {
        FrameworkInfo.Builder frameworkBuilder = FrameworkInfo.newBuilder()
            .setUser(user)
            .setName(name);

        org.apache.mesos.v1.Protos.FrameworkInfo frameworkInfo = Evolver.evolve(frameworkBuilder.buildPartial());

        assertEquals(user, frameworkInfo.getUser());
        assertEquals(name, frameworkInfo.getName());
    }
}
