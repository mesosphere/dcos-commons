package com.mesosphere.sdk.dcos.http;

import org.apache.http.impl.client.DefaultRedirectStrategy;

/**
 * A copy of {@link org.apache.http.impl.client.LaxRedirectStrategy} with addition support for PUT method redirect
 * that is necessary for SecretsClient.
 */
public class CustomRedirectStrategy extends DefaultRedirectStrategy {
    private static final String[] REDIRECT_METHODS = new String[]{"GET", "POST", "PUT", "HEAD", "DELETE"};

    public CustomRedirectStrategy() {
    }

    protected boolean isRedirectable(String method) {
        String[] arr$ = REDIRECT_METHODS;
        int len$ = arr$.length;

        for(int i$ = 0; i$ < len$; ++i$) {
            String m = arr$[i$];
            if (m.equalsIgnoreCase(method)) {
                return true;
            }
        }

        return false;
    }
}

