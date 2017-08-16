package com.mesosphere.sdk.dcos.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CachedTokenProviderTest {

    @Mock private DecodedJWT mockToken;
    @Mock private TokenProvider mockProvider;

    private CachedTokenProvider getProvider() {
        return new CachedTokenProvider(mockProvider, Duration.ofSeconds(30));
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetTokenRetrieval() throws IOException {
        when(mockToken.getExpiresAt()).thenReturn(
                Date.from(Instant.now().plusSeconds(60)));
        when(mockProvider.getToken()).thenReturn(mockToken);

        CachedTokenProvider cachedTokenProvider = getProvider();
        Assert.assertEquals(cachedTokenProvider.getToken(), mockToken);

        verify(mockProvider, times(1)).getToken();
    }

    @Test
    public void testGetTokenIsCached() throws IOException {
        when(mockToken.getExpiresAt()).thenReturn(
                Date.from(Instant.now().plusSeconds(60)));
        when(mockProvider.getToken()).thenReturn(mockToken);

        CachedTokenProvider cachedTokenProvider = getProvider();
        cachedTokenProvider.getToken();
        // Second call should be cached
        cachedTokenProvider.getToken();

        verify(mockToken, times(1)).getExpiresAt();
        verify(mockProvider, times(1)).getToken();
    }

    @Test
    public void testExpiredTokenIsRefreshed() throws IOException {
        // Create token that expired 60 seconds ago
        when(mockToken.getExpiresAt()).thenReturn(
                Date.from(Instant.now().minusSeconds(60)));
        when(mockProvider.getToken()).thenReturn(mockToken);

        CachedTokenProvider cachedTokenProvider = getProvider();
        cachedTokenProvider.getToken();
        // Second call should be cached
        cachedTokenProvider.getToken();

        verify(mockToken, times(1)).getExpiresAt();
        verify(mockProvider, times(2)).getToken();
    }

}
