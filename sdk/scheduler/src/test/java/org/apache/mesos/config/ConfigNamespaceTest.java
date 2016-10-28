package org.apache.mesos.config;

import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.Environment;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ConfigNamespace}.
 */
public class ConfigNamespaceTest {

    private static final Map<String, String> TEST_MAP = new HashMap<>();
    static {
        TEST_MAP.put("A_ONE", "TWO");
        TEST_MAP.put("A_THREE", "FOUR");
        TEST_MAP.put("A_B_FIVE", "SIX");
        TEST_MAP.put("B_SEVEN", "EIGHT");
        TEST_MAP.put("C_NINE", "TEN");
        TEST_MAP.put("C_", "EMPTY");
    }

    @Test
    public void testNoPrefixes() {
        ConfigNamespace cn = new ConfigNamespace(setOf(), TEST_MAP);
        assertTrue(cn.getAll().isEmpty());
    }

    @Test
    public void testNoConfig() {
        ConfigNamespace cn = new ConfigNamespace(setOf("A_"), new HashMap<>());
        assertTrue(cn.getAll().isEmpty());
    }

    @Test
    public void testDocumentedCase() {
        ConfigNamespace cn = new ConfigNamespace(setOf("A_", "C_"), TEST_MAP);
        assertEquals(5, cn.getAll().size());
        ConfigValue v = cn.get("ONE");
        assertEquals("TWO", v.requiredString());
        assertEquals("A_ONE=TWO", v.toString());
        v = cn.get("THREE");
        assertEquals("FOUR", v.requiredString());
        assertEquals("A_THREE=FOUR", v.toString());
        v = cn.get("B_FIVE");
        assertEquals("SIX", v.requiredString());
        assertEquals("A_B_FIVE=SIX", v.toString());
        v = cn.get("NINE");
        assertEquals("TEN", v.requiredString());
        assertEquals("C_NINE=TEN", v.toString());
        v = cn.get("");
        assertEquals("EMPTY", v.requiredString());
        assertEquals("C_=EMPTY", v.toString());
    }

    @Test
    public void testSinglePrefix() {
        ConfigNamespace cn = new ConfigNamespace(setOf("A_"), TEST_MAP);
        assertEquals(3, cn.getAll().size());
        ConfigValue v = cn.get("ONE");
        assertEquals("TWO", v.requiredString());
        assertEquals("A_ONE=TWO", v.toString());
        v = cn.get("THREE");
        assertEquals("FOUR", v.requiredString());
        assertEquals("A_THREE=FOUR", v.toString());
        v = cn.get("B_FIVE");
        assertEquals("SIX", v.requiredString());
        assertEquals("A_B_FIVE=SIX", v.toString());

        cn = new ConfigNamespace(setOf("C_"), TEST_MAP);
        assertEquals(2, cn.getAll().size());
        v = cn.get("NINE");
        assertEquals("TEN", v.requiredString());
        assertEquals("C_NINE=TEN", v.toString());
        v = cn.get("");
        assertEquals("EMPTY", v.requiredString());
        assertEquals("C_=EMPTY", v.toString());

    }

    @Test
    public void testOverlappingPrefixes() {
        ConfigNamespace cn = new ConfigNamespace(setOf("A_", "A_B_"), TEST_MAP);
        assertEquals(4, cn.getAll().size());
        ConfigValue v = cn.get("ONE");
        assertEquals("TWO", v.requiredString());
        assertEquals("A_ONE=TWO", v.toString());
        v = cn.get("THREE");
        assertEquals("FOUR", v.requiredString());
        assertEquals("A_THREE=FOUR", v.toString());
        v = cn.get("B_FIVE"); // matches A_* ...
        assertEquals("SIX", v.requiredString());
        assertEquals("A_B_FIVE=SIX", v.toString());
        v = cn.get("FIVE"); // ... AND matches A_B_*
        assertEquals("SIX", v.requiredString());
        assertEquals("A_B_FIVE=SIX", v.toString());
    }

    @Test
    public void testAddEmptyToEmptyEnv() {
        ConfigNamespace cn = new ConfigNamespace(setOf(), new HashMap<>());
        assertTrue(cn.getAll().isEmpty());
        CommandInfo cmd = cn.updateEnvironment(CommandInfo.newBuilder().build());
        assertFalse(cmd.hasEnvironment());
    }

    @Test
    public void testAddConfigToEmptyEnv() {
        ConfigNamespace cn = new ConfigNamespace(setOf("A_", "C_"), TEST_MAP);
        CommandInfo cmd = cn.updateEnvironment(CommandInfo.newBuilder().build());
        assertEquals(5, cmd.getEnvironment().getVariablesCount());
        Environment env = cmd.getEnvironment();

        // newly added entries in alphabetical order:

        assertEquals("", env.getVariables(0).getName());
        assertEquals("EMPTY", env.getVariables(0).getValue());

        assertEquals("B_FIVE", env.getVariables(1).getName());
        assertEquals("SIX", env.getVariables(1).getValue());

        assertEquals("NINE", env.getVariables(2).getName());
        assertEquals("TEN", env.getVariables(2).getValue());

        assertEquals("ONE", env.getVariables(3).getName());
        assertEquals("TWO", env.getVariables(3).getValue());

        assertEquals("THREE", env.getVariables(4).getName());
        assertEquals("FOUR", env.getVariables(4).getValue());
    }

    @Test
    public void testAddConfigToPreexistingEnvFullOverlap() {
        Environment.Builder envBuilder = Environment.newBuilder();
        envBuilder.addVariablesBuilder().setName("ONE").setValue("TEST");
        envBuilder.addVariablesBuilder().setName("THREE").setValue("TEST");
        envBuilder.addVariablesBuilder().setName("B_FIVE").setValue("TEST");
        envBuilder.addVariablesBuilder().setName("NINE").setValue("TEST");
        envBuilder.addVariablesBuilder().setName("").setValue("TEST");
        envBuilder.addVariablesBuilder().setName("TESTA").setValue("TESTB");

        ConfigNamespace cn = new ConfigNamespace(setOf("A_", "C_"), TEST_MAP);
        CommandInfo cmd = cn.updateEnvironment(CommandInfo.newBuilder().setEnvironment(envBuilder).build());
        assertEquals(6, cmd.getEnvironment().getVariablesCount());
        Environment env = cmd.getEnvironment();

        // matching preexisting entries removed, new values added to end in alphabetical order:

        // not present in TEST_MAP:

        assertEquals("TESTA", env.getVariables(0).getName());
        assertEquals("TESTB", env.getVariables(0).getValue());

        // from TEST_MAP:

        assertEquals("", env.getVariables(1).getName());
        assertEquals("EMPTY", env.getVariables(1).getValue());

        assertEquals("B_FIVE", env.getVariables(2).getName());
        assertEquals("SIX", env.getVariables(2).getValue());

        assertEquals("NINE", env.getVariables(3).getName());
        assertEquals("TEN", env.getVariables(3).getValue());

        assertEquals("ONE", env.getVariables(4).getName());
        assertEquals("TWO", env.getVariables(4).getValue());

        assertEquals("THREE", env.getVariables(5).getName());
        assertEquals("FOUR", env.getVariables(5).getValue());
    }

    @Test
    public void testAddConfigToPreexistingEnvPartialOverlap() {
        Environment.Builder envBuilder = Environment.newBuilder();
        envBuilder.addVariablesBuilder().setName("ONE").setValue("TEST");
        envBuilder.addVariablesBuilder().setName("B_FIVE").setValue("TEST");
        envBuilder.addVariablesBuilder().setName("").setValue("TEST");
        envBuilder.addVariablesBuilder().setName("TESTA").setValue("TESTB");

        ConfigNamespace cn = new ConfigNamespace(setOf("A_", "C_"), TEST_MAP);
        CommandInfo cmd = cn.updateEnvironment(CommandInfo.newBuilder().setEnvironment(envBuilder).build());
        assertEquals(6, cmd.getEnvironment().getVariablesCount());
        Environment env = cmd.getEnvironment();

        // matching preexisting entries removed, new values added to end in alphabetical order:

        // not present in TEST_MAP:

        assertEquals("TESTA", env.getVariables(0).getName());
        assertEquals("TESTB", env.getVariables(0).getValue());

        // from TEST_MAP:

        assertEquals("", env.getVariables(1).getName());
        assertEquals("EMPTY", env.getVariables(1).getValue());

        assertEquals("B_FIVE", env.getVariables(2).getName());
        assertEquals("SIX", env.getVariables(2).getValue());

        assertEquals("NINE", env.getVariables(3).getName());
        assertEquals("TEN", env.getVariables(3).getValue());

        assertEquals("ONE", env.getVariables(4).getName());
        assertEquals("TWO", env.getVariables(4).getValue());

        assertEquals("THREE", env.getVariables(5).getName());
        assertEquals("FOUR", env.getVariables(5).getValue());
    }

    private static Set<String> setOf(String... entries) {
        return new HashSet<>(Arrays.asList(entries));
    }
}
