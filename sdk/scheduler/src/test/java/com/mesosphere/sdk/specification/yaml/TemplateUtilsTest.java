package com.mesosphere.sdk.specification.yaml;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * Tests for {@link TemplateUtils}.
 */
public class TemplateUtilsTest {

    @Test
    public void testMustacheFullyRendered() throws IOException {
        Assert.assertTrue(TemplateUtils.isMustacheFullyRendered(null));
        Assert.assertTrue(TemplateUtils.isMustacheFullyRendered(""));
        Assert.assertTrue(TemplateUtils.isMustacheFullyRendered("foo"));
        Assert.assertTrue(TemplateUtils.isMustacheFullyRendered("}}asdf{{"));
        Assert.assertFalse(TemplateUtils.isMustacheFullyRendered("{{FOOOOOO}}"));
        Assert.assertFalse(TemplateUtils.isMustacheFullyRendered("aoeu{{FOOOOOO}}asdf"));
        Assert.assertFalse(TemplateUtils.isMustacheFullyRendered("aoeu\n\n{{FOOOOOO}}\n\nasdf"));
    }

    @Test
    public void testApplyEnvToMustache() throws IOException {
        File file = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        String yaml = FileUtils.readFileToString(file);
        Assert.assertTrue(yaml.contains("api-port: {{PORT_API}}"));
        Assert.assertFalse(yaml, TemplateUtils.isMustacheFullyRendered(yaml));

        Map<String, String> envMap = new HashMap<>();
        envMap.put("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
        String renderedYaml = TemplateUtils.applyEnvToMustache(yaml, envMap);

        Assert.assertTrue(renderedYaml.contains(String.format("api-port: %d", TestConstants.PORT_API_VALUE)));

        // No other template params to populate:
        Assert.assertTrue(TemplateUtils.isMustacheFullyRendered(renderedYaml));
    }
}
