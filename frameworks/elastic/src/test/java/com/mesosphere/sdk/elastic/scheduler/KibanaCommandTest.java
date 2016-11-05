package com.mesosphere.sdk.elastic.scheduler;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class KibanaCommandTest {
    private KibanaCommand kibanaCommand;

    @Before
    public void before() throws Exception {
        kibanaCommand = new KibanaCommand("5.0.0-beta1",
            "https://artifacts.elastic.co/downloads/packs/x-pack/x-pack-5.0.0-beta1.zip");
    }

    @Test
    public void getCommandLineInvocation() throws Exception {
        String commandLineInvocation = kibanaCommand.getCommandLineInvocation();
        Assert.assertEquals(commandLineInvocation, "$MESOS_SANDBOX/5.0.0-beta1/bin/kibana-plugin install file://$MESOS_SANDBOX/x-pack-5.0.0-beta1.zip && exec $MESOS_SANDBOX/5.0.0-beta1/bin/kibana -c kibana.yml");
    }

}