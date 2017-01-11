package com.mesosphere.sdk.config;

import com.google.common.collect.ImmutableMap;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultTaskConfigRouter}.
 */
public class DefaultTaskConfigRouterTest {
    private static TaskSpec mockTaskSpec = mock(TaskSpec.class);
    private static List<TaskSpec> taskSpecs = Arrays.asList(mockTaskSpec);

    static {
        when(mockTaskSpec.getName()).thenReturn("mockTask");
    }

    private static final PodSpec TASK_SET_A = DefaultPodSpec.newBuilder()
            .type("a")
            .count(0)
            .tasks(taskSpecs)
            .build();
    private static final PodSpec TASK_SET_B = DefaultPodSpec.newBuilder()
            .type("b")
            .count(0)
            .tasks(taskSpecs)
            .build();
    private static final PodSpec TASK_SET_C = DefaultPodSpec.newBuilder()
            .type("c")
            .count(0)
            .tasks(taskSpecs)
            .build();
    private static final PodSpec TASK_SET_D = DefaultPodSpec.newBuilder()
            .type("d")
            .count(0)
            .tasks(taskSpecs)
            .build();

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
        TaskConfigRouter router = new DefaultTaskConfigRouter(new HashMap<>());
        assertTrue(router.getConfig(TASK_SET_A.getType()).getAll().isEmpty());
        assertTrue(router.getConfig(TASK_SET_B.getType()).getAll().isEmpty());
        assertTrue(router.getConfig(TASK_SET_C.getType()).getAll().isEmpty());
        assertTrue(router.getConfig(TASK_SET_D.getType()).getAll().isEmpty());
        assertTrue(router.getConfig("E").getAll().isEmpty());
    }

    @Test
    public void testMixedConfig() {
        TaskConfigRouter router = new DefaultTaskConfigRouter(TEST_MAP);
        ImmutableMap<String, ConfigValue> values = router.getConfig(TASK_SET_A.getType()).getAll();
        assertEquals(5, values.size());
        assertEquals("TWO", values.get("ONE").requiredString());
        assertEquals("TASKCFG_A_ONE=TWO", values.get("ONE").toString());
        assertEquals("FOUR", values.get("THREE").requiredString());
        assertEquals("TASKCFG_A_THREE=FOUR", values.get("THREE").toString());
        assertEquals("SIX", values.get("B_FIVE").requiredString());
        assertEquals("TASKCFG_A_B_FIVE=SIX", values.get("B_FIVE").toString());
        assertEquals("BAR", values.get("FOO").requiredString());
        assertEquals("TASKCFG_ALL_FOO=BAR", values.get("FOO").toString());
        assertEquals("BAZ", values.get("BAR").requiredString());
        assertEquals("TASKCFG_ALL_BAR=BAZ", values.get("BAR").toString());

        values = router.getConfig(TASK_SET_B.getType()).getAll();
        assertEquals(3, values.size());
        assertEquals("EIGHT", values.get("SEVEN").requiredString());
        assertEquals("TASKCFG_B_SEVEN=EIGHT", values.get("SEVEN").toString());
        assertEquals("BAR", values.get("FOO").requiredString());
        assertEquals("TASKCFG_ALL_FOO=BAR", values.get("FOO").toString());
        assertEquals("BAZ", values.get("BAR").requiredString());
        assertEquals("TASKCFG_ALL_BAR=BAZ", values.get("BAR").toString());

        values = router.getConfig(TASK_SET_C.getType()).getAll();
        assertEquals(3, values.size());
        assertEquals("TEN", values.get("NINE").requiredString());
        assertEquals("TASKCFG_C_NINE=TEN", values.get("NINE").toString());
        assertEquals("BAR", values.get("FOO").requiredString());
        assertEquals("TASKCFG_ALL_FOO=BAR", values.get("FOO").toString());
        assertEquals("BAZ", values.get("BAR").requiredString());
        assertEquals("TASKCFG_ALL_BAR=BAZ", values.get("BAR").toString());

        values = router.getConfig(TASK_SET_D.getType()).getAll();
        assertEquals(2, values.size());
        assertEquals("BAR", values.get("FOO").requiredString());
        assertEquals("TASKCFG_ALL_FOO=BAR", values.get("FOO").toString());
        assertEquals("BAZ", values.get("BAR").requiredString());
        assertEquals("TASKCFG_ALL_BAR=BAZ", values.get("BAR").toString());

        values = router.getConfig("E").getAll();
        assertEquals(2, values.size());
        assertEquals("BAR", values.get("FOO").requiredString());
        assertEquals("TASKCFG_ALL_FOO=BAR", values.get("FOO").toString());
        assertEquals("BAZ", values.get("BAR").requiredString());
        assertEquals("TASKCFG_ALL_BAR=BAZ", values.get("BAR").toString());
    }

    @Test
    public void testTypeMapping() {
        TaskConfigRouter router = new DefaultTaskConfigRouter(TEST_MAP);
        ImmutableMap<String, ConfigValue> valuesUpperUnderscore = router.getConfig("A_B").getAll();
        assertEquals(3, valuesUpperUnderscore.size());
        assertEquals(valuesUpperUnderscore, router.getConfig("A-B").getAll());
        assertEquals(valuesUpperUnderscore, router.getConfig("A.B").getAll());
        assertEquals(valuesUpperUnderscore, router.getConfig("a_b").getAll());
        assertEquals(valuesUpperUnderscore, router.getConfig("a.b").getAll());
        assertEquals(valuesUpperUnderscore, router.getConfig("A-B").getAll());
    }
}
