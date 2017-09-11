package com.mesosphere.sdk.specification.yaml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.github.mustachejava.MustacheException;
import com.mesosphere.sdk.specification.yaml.TemplateUtils.MissingValue;

/**
 * Tests for {@link TemplateUtils}.
 */
public class TemplateUtilsTest {

    @Test
    public void testApplyEnvToExhaustiveMustache() throws IOException {
        String filename = "test-render.yml";
        File file = new File(getClass().getClassLoader().getResource(filename).getFile());
        String yaml = FileUtils.readFileToString(file);
        Assert.assertTrue(yaml.contains("size: {{VOL_SIZE}}"));

        Map<String, String> envMap = new HashMap<>();
        envMap.put("VOL_SIZE", String.valueOf(1024));

        String renderedYaml = TemplateUtils.renderMustacheThrowIfMissing(filename, yaml, envMap);
        Assert.assertTrue(renderedYaml.contains(String.format("size: %d", 1024)));
    }

    @Test
    public void testApplyMissingEmptyString() throws IOException {
        List<MissingValue> missing = new ArrayList<>();
        Assert.assertEquals("hello this is a . thanks for reading bye", TemplateUtils.renderMustache(
                "testTemplate",
                "hello this is a {{missing-parameter}}. thanks for reading bye",
                Collections.emptyMap(),
                missing));
        Assert.assertEquals(1, missing.size());
        Assert.assertEquals(1, missing.get(0).line);
        Assert.assertEquals("missing-parameter", missing.get(0).name);
    }

    @Test
    public void testApplyMissingExceptionValueThrows() throws IOException {
        Map<String, String> env = new TreeMap<>();
        env.put("foo", "bar");
        env.put("bar", "baz");
        env.put("baz", "foo");
        try {
            TemplateUtils.renderMustacheThrowIfMissing(
                    "testTemplate",
                    "hello this is {{a_missing_parameter}},\nand {{another_missing_parameter}}. thanks for reading bye",
                    env);
            Assert.fail("expected exception");
        } catch (MustacheException e) {
            Assert.assertEquals(String.format(
                    "Missing 2 values when rendering testTemplate:%n" +
                    "- Missing values: [a_missing_parameter@L1, another_missing_parameter@L2]%n" +
                    "- Provided values: %s", env), e.getMessage());
        }
    }

    @Test
    public void testApplyMissingExceptionEnableBlockPasses() throws IOException {
        Assert.assertEquals("hello this is an . thanks for reading bye", TemplateUtils.renderMustacheThrowIfMissing(
                "testTemplate",
                "hello this is an {{#missing_parameter}}ignored string{{/missing_parameter}}. thanks for reading bye",
                Collections.emptyMap()));
    }

    @Test
    public void testApplyMissingExceptionDisableBlockPasses() throws IOException {
        Assert.assertEquals("hello this is an included string. thanks for reading bye",
                TemplateUtils.renderMustacheThrowIfMissing(
                "testTemplate",
                "hello this is an {{^missing_parameter}}included string{{/missing_parameter}}. thanks for reading bye",
                Collections.emptyMap()));
    }

    @Test
    public void testApplyTrueEnvVarToSection() throws IOException {
        String filename = "test-render-inverted.yml";
        String yaml = getYamlContent(filename);
        Assert.assertTrue(yaml.contains("ENABLED"));

        Map<String, String> envMap = new HashMap<>();
        envMap.put("ENABLED", String.valueOf(true));
        String renderedYaml = TemplateUtils.renderMustacheThrowIfMissing(filename, yaml, envMap);

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
        String renderedYaml = TemplateUtils.renderMustacheThrowIfMissing(filename, yaml, envMap);

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
        String renderedYaml = TemplateUtils.renderMustacheThrowIfMissing(filename, yaml, envMap);

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
