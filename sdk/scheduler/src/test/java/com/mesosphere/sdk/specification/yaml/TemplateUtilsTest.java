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
        String filename = "test-render.yml";
        File file = new File(getClass().getClassLoader().getResource(filename).getFile());
        String yaml = FileUtils.readFileToString(file);
        Assert.assertTrue(yaml.contains("size: {{VOL_SIZE}}"));
        Assert.assertFalse(yaml, TemplateUtils.isMustacheFullyRendered(yaml));

        Map<String, String> envMap = new HashMap<>();
        envMap.put("VOL_SIZE", String.valueOf(1024));
        String renderedYaml = TemplateUtils.applyEnvToMustache(
                filename, yaml, envMap, TemplateUtils.MissingBehavior.EMPTY_STRING);

        Assert.assertTrue(renderedYaml.contains(String.format("size: %d", 1024)));

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

    @Test
    public void testApplyTrueEnvVarToSection() throws IOException {
        String filename = "test-render-inverted.yml";
        String yaml = getYamlContent(filename);
        Assert.assertTrue(yaml.contains("ENABLED"));

        Map<String, String> envMap = new HashMap<>();
        envMap.put("ENABLED", String.valueOf(true));
        String renderedYaml = TemplateUtils.applyEnvToMustache(
                filename, yaml, envMap, TemplateUtils.MissingBehavior.EMPTY_STRING);

        Assert.assertTrue(renderedYaml.contains("cmd: ./enabled true"));
        Assert.assertFalse(renderedYaml.contains("cmd: ./disabled"));
        Assert.assertFalse(renderedYaml.contains("ENABLED"));
    }

    @Test
    public void testApplyFalseEnvVarToInvertedSection() throws IOException {
        String filename = "test-render-inverted.yml";
        String yaml = getYamlContent(filename);
        Assert.assertTrue(yaml.contains("ENABLED"));

        Map<String, String> envMap = new HashMap<>();
        envMap.put("ENABLED", String.valueOf(false));
        String renderedYaml = TemplateUtils.applyEnvToMustache(
                filename, yaml, envMap, TemplateUtils.MissingBehavior.EMPTY_STRING);

        Assert.assertTrue(renderedYaml.contains("cmd: ./disabled false"));
        Assert.assertFalse(renderedYaml.contains("cmd: ./enabled"));
        Assert.assertFalse(renderedYaml.contains("ENABLED"));
    }

    @Test
    public void testApplyEmptyEnvVarToInvertedSection() throws IOException {
        String filename = "test-render-inverted.yml";
        String yaml = getYamlContent(filename);
        Assert.assertTrue(yaml.contains("ENABLED"));

        Map<String, String> envMap = new HashMap<>();
        envMap.put("ENABLED", "");
        String renderedYaml = TemplateUtils.applyEnvToMustache(
                filename, yaml, envMap, TemplateUtils.MissingBehavior.EMPTY_STRING);

        Assert.assertTrue(renderedYaml.contains("cmd: ./disabled"));
        Assert.assertFalse(renderedYaml.contains("cmd: ./disabled false"));
        Assert.assertFalse(renderedYaml.contains("cmd: ./enabled"));
        Assert.assertFalse(renderedYaml.contains("ENABLED"));
    }

    private String getYamlContent(String fileName) throws IOException {
        File file = new File(getClass().getClassLoader().getResource(fileName).getFile());
        return FileUtils.readFileToString(file);
    }
}
