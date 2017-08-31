package com.mesosphere.sdk.specification.yaml;

import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class tests {@link YAMLToInternalMappers}.
 */
public class YamlToInternalMappersTest {
    private static final String firstUriStr = "https://github.com/mesosphere/dcos-commons/blob/master/README.md";
    private static final String secondUriStr = "https://github.com/mesosphere/dcos-commons/blob/master/docs/generate.sh";
    private static final String multipleSpacedUriStr = firstUriStr + ", " + secondUriStr;
    private static final String multipleNoSpaceUriStr = firstUriStr + "," + secondUriStr;

    @Test(expected = URISyntaxException.class)
    public void testEmptyUriString() throws URISyntaxException {
        YAMLToInternalMappers.getUris("");
    }

    @Test
    public void testSeparateSingleUriString() throws URISyntaxException {
        URI uri = YAMLToInternalMappers.getUris(firstUriStr).stream().findAny().get();
        Assert.assertEquals(firstUriStr, uri.toString());
    }

    @Test
    public void testSeparateCommaWithSpace() throws URISyntaxException {
        List<URI> uris = YAMLToInternalMappers.getUris(multipleSpacedUriStr).stream().collect(Collectors.toList());
        Assert.assertEquals(firstUriStr, uris.get(0).toString());
        Assert.assertEquals(secondUriStr, uris.get(1).toString());
    }

    @Test
    public void testSeparateCommaWithoutSpace() throws URISyntaxException {
        List<URI> uris = YAMLToInternalMappers.getUris(multipleNoSpaceUriStr).stream().collect(Collectors.toList());
        Assert.assertEquals(firstUriStr, uris.get(0).toString());
        Assert.assertEquals(secondUriStr, uris.get(1).toString());
    }
}
