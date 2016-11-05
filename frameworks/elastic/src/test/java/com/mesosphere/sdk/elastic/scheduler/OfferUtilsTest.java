package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

public class OfferUtilsTest {
    @Test
    public void idToName() throws Exception {
        Assert.assertEquals("foo-1", OfferUtils.idToName("foo", 1));
    }

    @Test
    public void nameToTaskType() throws Exception {
        Assert.assertEquals("foo", OfferUtils.nameToTaskType("foo-1"));
    }

    @Test
    public void buildSinglePortRange() throws Exception {
        Protos.Value.Range range = OfferUtils.buildSinglePortRange(1234);
        Assert.assertEquals(1234L, range.getBegin());
    }

    @Test
    public void createEnvironmentVariable() throws Exception {
        Protos.Environment.Variable variable = OfferUtils.createEnvironmentVariable("foo", "bar");
        Assert.assertEquals("foo", variable.getName());
        Assert.assertEquals("bar", variable.getValue());
    }

}