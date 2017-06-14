package com.mesosphere.sdk.specification.yaml;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.github.mustachejava.MustacheException;
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
    public void testApplyEnvToExhaustiveMustache() throws IOException {
        String filename = "valid-exhaustive.yml";
        File file = new File(getClass().getClassLoader().getResource(filename).getFile());
        String yaml = FileUtils.readFileToString(file);
        Assert.assertTrue(yaml.contains("api-port: {{PORT_API}}"));
        Assert.assertFalse(yaml, TemplateUtils.isMustacheFullyRendered(yaml));

        Map<String, String> envMap = new HashMap<>();
        envMap.put("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
        String renderedYaml = TemplateUtils.applyEnvToMustache(
                filename, yaml, envMap, TemplateUtils.MissingBehavior.EMPTY_STRING);

        Assert.assertTrue(renderedYaml.contains(String.format("api-port: %d", TestConstants.PORT_API_VALUE)));

        // No other template params to populate:
        Assert.assertTrue(TemplateUtils.isMustacheFullyRendered(renderedYaml));
    }

    @Test
    public void testApplyMissingEmptyString() throws IOException {
        Assert.assertEquals("hello this is a . thanks for reading bye", TemplateUtils.applyEnvToMustache(
                "testTemplate",
                "hello this is a {{missing-parameter}}. thanks for reading bye",
                Collections.emptyMap(),
                TemplateUtils.MissingBehavior.EMPTY_STRING));
    }

    @Test
    public void testApplyMissingExceptionValueThrows() throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("foo", "bar");
        env.put("bar", "baz");
        env.put("baz", "foo");
        try {
            TemplateUtils.applyEnvToMustache(
                    "testTemplate",
                    "hello this is a {{missing_parameter}}. thanks for reading bye",
                    env,
                    TemplateUtils.MissingBehavior.EXCEPTION);
            Assert.fail("expected exception");
        } catch (MustacheException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("Failed to get value for missing_parameter @[testTemplate:1]"));
            String causeMessage = "Template param missing_parameter was not found at template line 1:\n"
                    + "- env:\n"
                    + " bar=baz\n"
                    + " baz=foo\n"
                    + " foo=bar\n"
                    + "- template:\n"
                    + "hello this is a {{missing_parameter}}. thanks for reading bye\n"
                    + "- code: com.github.mustachejava.codes.ValueCode";
            Assert.assertTrue(e.getCause().getMessage(), e.getCause().getMessage().startsWith(causeMessage));
        }
    }

    @Test
    public void testApplyMissingExceptionEnableBlockPasses() throws IOException {
        Assert.assertEquals("hello this is an . thanks for reading bye", TemplateUtils.applyEnvToMustache(
                "testTemplate",
                "hello this is an {{#missing_parameter}}ignored string{{/missing_parameter}}. thanks for reading bye",
                Collections.emptyMap(),
                TemplateUtils.MissingBehavior.EXCEPTION));
    }

    @Test
    public void testApplyMissingExceptionDisableBlockPasses() throws IOException {
        Assert.assertEquals("hello this is an included string. thanks for reading bye", TemplateUtils.applyEnvToMustache(
                "testTemplate",
                "hello this is an {{^missing_parameter}}included string{{/missing_parameter}}. thanks for reading bye",
                Collections.emptyMap(),
                TemplateUtils.MissingBehavior.EXCEPTION));
    }
}
