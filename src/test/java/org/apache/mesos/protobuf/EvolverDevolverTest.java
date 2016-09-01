package org.apache.mesos.protobuf;

import org.apache.mesos.v1.Protos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class EvolverDevolverTest {
    private final static String user = "test-user";
    private final static String name = "test-framework";
    private final static String id = "test-id";
    private final static String principal = "test-principal";
    private final static String secret = "test-secret";

    @Test
    public void testFrameworkInfoEvolve() {
        org.apache.mesos.Protos.FrameworkInfo.Builder frameworkBuilder = org.apache.mesos.Protos.FrameworkInfo.newBuilder()
            .setUser(user)
            .setName(name);

        Protos.FrameworkInfo frameworkInfo = EvolverDevolver.evolve(frameworkBuilder.buildPartial());

        assertEquals(user, frameworkInfo.getUser());
        assertEquals(name, frameworkInfo.getName());
    }

    @Test
    public void testFrameworkInfoDevolve() {
        Protos.FrameworkInfo.Builder frameworkBuilder = Protos.FrameworkInfo.newBuilder()
                .setUser(user)
                .setName(name);

        org.apache.mesos.Protos.FrameworkInfo frameworkInfo = EvolverDevolver.devolve(frameworkBuilder.buildPartial());

        assertEquals(user, frameworkInfo.getUser());
        assertEquals(name, frameworkInfo.getName());
    }

    @Test
    public void testFrameworkIdEvolve() {
        org.apache.mesos.Protos.FrameworkID.Builder builder = org.apache.mesos.Protos.FrameworkID.newBuilder()
                .setValue(id);

        Protos.FrameworkID entity = EvolverDevolver.evolve(builder.buildPartial());

        assertEquals(id, entity.getValue());
    }

    @Test
    public void testFrameworkIdDevolve() {
        final Protos.FrameworkID.Builder builder = Protos.FrameworkID.newBuilder().setValue(id);

        org.apache.mesos.Protos.FrameworkID entity = EvolverDevolver.devolve(builder.buildPartial());

        assertEquals(id, entity.getValue());
    }

    @Test
    public void testCredentialEvolve() {
        org.apache.mesos.Protos.Credential.Builder builder = org.apache.mesos.Protos.Credential.newBuilder()
                .setPrincipal(principal)
                .setSecret(secret);

        Protos.Credential entity = EvolverDevolver.evolve(builder.buildPartial());

        assertEquals(principal, entity.getPrincipal());
        assertEquals(secret, entity.getSecret());
    }
}
