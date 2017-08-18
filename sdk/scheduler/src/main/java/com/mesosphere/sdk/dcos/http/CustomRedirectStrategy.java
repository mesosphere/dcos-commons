package com.mesosphere.sdk.dcos.http;

import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.LaxRedirectStrategy;

/**
 * Extension of LaxRedirectStrategy that adds PUT as a re-directable method.
 */
public class CustomRedirectStrategy extends LaxRedirectStrategy {

    protected boolean isRedirectable(String method) {
        return method.equalsIgnoreCase(HttpPut.METHOD_NAME) || super.isRedirectable(method);
    }
}
