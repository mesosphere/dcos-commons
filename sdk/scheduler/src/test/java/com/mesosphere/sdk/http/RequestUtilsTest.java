package com.mesosphere.sdk.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class RequestUtilsTest {

    private static final int SIZE_LIMIT = 8;
    private static final byte[] LIMIT_EXCEED = "123456789".getBytes(StandardCharsets.UTF_8);
    private static final long LIMIT_EXCEED_LENGTH = LIMIT_EXCEED.length;
    private static final byte[] LIMIT_MATCH = "12345678".getBytes(StandardCharsets.UTF_8);
    private static final long LIMIT_MATCH_LENGTH = LIMIT_MATCH.length;
    private static final byte[] LIMIT_UNDER = "1234567".getBytes(StandardCharsets.UTF_8);
    private static final long LIMIT_UNDER_LENGTH = LIMIT_UNDER.length;

    @Mock private InputStream mockInputStream;
    @Mock private FormDataContentDisposition mockFileDetails;

    @BeforeClass
    public static void beforeAll() {
        // Sanity check:
        Assert.assertTrue(LIMIT_EXCEED.length > SIZE_LIMIT);
        Assert.assertTrue(LIMIT_MATCH.length == SIZE_LIMIT);
        Assert.assertTrue(LIMIT_UNDER.length < SIZE_LIMIT);
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadDataNullStream() throws IOException {
        RequestUtils.readData(null, mockFileDetails, SIZE_LIMIT);
    }

    public void testReadDataNullDetails() throws IOException {
        Assert.assertArrayEquals(LIMIT_MATCH,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_MATCH), null, SIZE_LIMIT));
    }

    @Test
    public void testReadNoLimit() throws IOException {
        when(mockFileDetails.getSize()).thenReturn(LIMIT_EXCEED_LENGTH);
        Assert.assertArrayEquals(LIMIT_EXCEED,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_EXCEED), mockFileDetails, 0));
        Assert.assertArrayEquals(LIMIT_EXCEED,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_EXCEED), mockFileDetails, -1));

        when(mockFileDetails.getSize()).thenReturn(LIMIT_MATCH_LENGTH);
        Assert.assertArrayEquals(LIMIT_MATCH,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_MATCH), mockFileDetails, 0));
        Assert.assertArrayEquals(LIMIT_MATCH,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_MATCH), mockFileDetails, -1));
    }

    @Test
    public void testReadMetadataSizeExceeded() throws IOException {
        // If declared length exceeds limit, exit early without reading stream:
        when(mockFileDetails.getSize()).thenReturn(LIMIT_EXCEED_LENGTH);
        try {
            RequestUtils.readData(mockInputStream, mockFileDetails, SIZE_LIMIT);
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
        verifyZeroInteractions(mockInputStream);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadStreamSizeExceededMaliciousMetadata() throws IOException {
        when(mockFileDetails.getSize()).thenReturn(LIMIT_MATCH_LENGTH);
        RequestUtils.readData(new ByteArrayInputStream(LIMIT_EXCEED), mockFileDetails, SIZE_LIMIT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadStreamSizeExceededNoMetadata() throws IOException {
        RequestUtils.readData(new ByteArrayInputStream(LIMIT_EXCEED), null, SIZE_LIMIT);
    }

    @Test
    public void testReadSizeMatch() throws IOException {
        // Regardless of declared size in metadata, the stream gets through correctly:

        when(mockFileDetails.getSize()).thenReturn(LIMIT_MATCH_LENGTH);
        Assert.assertArrayEquals(LIMIT_MATCH,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_MATCH), mockFileDetails, SIZE_LIMIT));

        when(mockFileDetails.getSize()).thenReturn(LIMIT_MATCH_LENGTH - 1);
        Assert.assertArrayEquals(LIMIT_MATCH,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_MATCH), mockFileDetails, SIZE_LIMIT));

        when(mockFileDetails.getSize()).thenReturn(-1L);
        Assert.assertArrayEquals(LIMIT_MATCH,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_MATCH), mockFileDetails, SIZE_LIMIT));

        Assert.assertArrayEquals(LIMIT_MATCH,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_MATCH), null, SIZE_LIMIT));
    }

    @Test
    public void testReadSizeUnder() throws IOException {
        // Regardless of declared size in metadata, the stream gets through correctly:

        when(mockFileDetails.getSize()).thenReturn(LIMIT_UNDER_LENGTH);
        Assert.assertArrayEquals(LIMIT_UNDER,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_UNDER), mockFileDetails, SIZE_LIMIT));

        when(mockFileDetails.getSize()).thenReturn(LIMIT_UNDER_LENGTH - 1);
        Assert.assertArrayEquals(LIMIT_UNDER,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_UNDER), mockFileDetails, SIZE_LIMIT));

        when(mockFileDetails.getSize()).thenReturn(-1L);
        Assert.assertArrayEquals(LIMIT_UNDER,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_UNDER), mockFileDetails, SIZE_LIMIT));

        Assert.assertArrayEquals(LIMIT_UNDER,
                RequestUtils.readData(new ByteArrayInputStream(LIMIT_UNDER), null, SIZE_LIMIT));
    }
}
