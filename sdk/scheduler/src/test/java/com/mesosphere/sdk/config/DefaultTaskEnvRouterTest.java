package com.mesosphere.sdk.config;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link TaskEnvRouter}.
 */
public class DefaultTaskEnvRouterTest {
    private static final Map<String, String> TEST_MAP = new HashMap<>();
    static {
        TEST_MAP.put("TASKCFG_A_ONE", "TWO");
        TEST_MAP.put("TASKCFG_A_THREE", "FOUR");
        TEST_MAP.put("TASKCFG_A_B_FIVE", "SIX");
        TEST_MAP.put("TASKCFG_B_SEVEN", "EIGHT");
        TEST_MAP.put("TASKCFG_C_NINE", "TEN");
        TEST_MAP.put("TASKCFG_ALL_FOO", "BAR");
        TEST_MAP.put("TASKCFG_ALL_BAR", "BAZ");
        TEST_MAP.put("TASKCFG_IGNORED", "FOO");
        TEST_MAP.put("IGNORED", "BAR");
    }

    @Test
    public void testEmptyConfig() {
        TaskEnvRouter router = new TaskEnvRouter(new HashMap<>());
        assertTrue(router.getConfig("a").isEmpty());
        assertTrue(router.getConfig("b").isEmpty());
        assertTrue(router.getConfig("c").isEmpty());
        assertTrue(router.getConfig("d").isEmpty());
        assertTrue(router.getConfig("e").isEmpty());
    }

    @Test
    public void testMixedConfig() {
        TaskEnvRouter router = new TaskEnvRouter(TEST_MAP);
        Map<String, String> values = router.getConfig("a");
        assertEquals(values, router.getConfig("A")); // case insensitive
        assertEquals(5, values.size());
        assertEquals("TWO", values.get("ONE"));
        assertEquals("FOUR", values.get("THREE"));
        assertEquals("SIX", values.get("B_FIVE"));
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));

        values = router.getConfig("b");
        assertEquals(3, values.size());
        assertEquals("EIGHT", values.get("SEVEN"));
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));

        values = router.getConfig("c");
        assertEquals(3, values.size());
        assertEquals("TEN", values.get("NINE"));
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));

        values = router.getConfig("d");
        assertEquals(2, values.size());
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));

        values = router.getConfig("e");
        assertEquals(2, values.size());
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));
    }

    @Test
    public void testTypeMapping() {
        TaskEnvRouter router = new TaskEnvRouter(TEST_MAP);
        Map<String, String> valuesUpperUnderscore = router.getConfig("A_B");
        assertEquals(3, valuesUpperUnderscore.size());
        assertEquals(valuesUpperUnderscore, router.getConfig("A-B"));
        assertEquals(valuesUpperUnderscore, router.getConfig("A.B"));
        assertEquals(valuesUpperUnderscore, router.getConfig("a_b"));
        assertEquals(valuesUpperUnderscore, router.getConfig("a.b"));
        assertEquals(valuesUpperUnderscore, router.getConfig("A-B"));
    }

    @Test
    public void testPriorities() {
        TaskEnvRouter router = new TaskEnvRouter(TEST_MAP)
                .setAllPodsEnv("NOT_IGNORED", "VAL")
                .setAllPodsEnv("ONE", "FOUR") // ignored in pod A, added in others
                .setPodEnv("A", "NOT_IGNORED", "VAL2") // overrides VAL in pod A
                .setPodEnv("A", "THREE", "EIGHT"); // ignored

        Map<String, String> values = router.getConfig("a");
        assertEquals(values, router.getConfig("A")); // case insensitive
        assertEquals(6, values.size());
        assertEquals("VAL2", values.get("NOT_IGNORED"));
        assertEquals("TWO", values.get("ONE"));
        assertEquals("FOUR", values.get("THREE"));
        assertEquals("SIX", values.get("B_FIVE"));
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));

        values = router.getConfig("b");
        assertEquals(5, values.size());
        assertEquals("VAL", values.get("NOT_IGNORED"));
        assertEquals("FOUR", values.get("ONE"));
        assertEquals("EIGHT", values.get("SEVEN"));
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));

        values = router.getConfig("c");
        assertEquals(5, values.size());
        assertEquals("VAL", values.get("NOT_IGNORED"));
        assertEquals("FOUR", values.get("ONE"));
        assertEquals("TEN", values.get("NINE"));
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));

        values = router.getConfig("d");
        assertEquals(4, values.size());
        assertEquals("VAL", values.get("NOT_IGNORED"));
        assertEquals("FOUR", values.get("ONE"));
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));

        values = router.getConfig("e");
        assertEquals(4, values.size());
        assertEquals("VAL", values.get("NOT_IGNORED"));
        assertEquals("FOUR", values.get("ONE"));
        assertEquals("BAR", values.get("FOO"));
        assertEquals("BAZ", values.get("BAR"));
    }
}
