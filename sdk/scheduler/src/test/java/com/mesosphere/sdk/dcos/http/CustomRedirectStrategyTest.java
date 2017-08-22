package com.mesosphere.sdk.dcos.http;


import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPut;
import org.junit.Assert;
import org.junit.Test;

public class CustomRedirectStrategyTest {

    @Test
    public void testPut() {
        Assert.assertEquals(true, new CustomRedirectStrategy().isRedirectable(HttpPut.METHOD_NAME));
    }

    @Test
    public void testGet() {
        Assert.assertEquals(true, new CustomRedirectStrategy().isRedirectable(HttpGet.METHOD_NAME));
    }

    @Test
    public void testPatch() {
        Assert.assertEquals(false, new CustomRedirectStrategy().isRedirectable(HttpPatch.METHOD_NAME));
    }

}
