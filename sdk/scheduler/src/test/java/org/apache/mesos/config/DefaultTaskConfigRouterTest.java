package org.apache.mesos.config;

import com.google.common.collect.ImmutableMap;
import org.apache.mesos.specification.DefaultPodSpec;
import org.apache.mesos.specification.PodSpec;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link DefaultTaskConfigRouter}.
 */
public class DefaultTaskConfigRouterTest {

    private static final PodSpec TASK_SET_A = DefaultPodSpec.newBuilder().type("a").build();
    private static final PodSpec TASK_SET_B = DefaultPodSpec.newBuilder().type("b").build();
    private static final PodSpec TASK_SET_C = DefaultPodSpec.newBuilder().type("c").build();
    private static final PodSpec TASK_SET_D = DefaultPodSpec.newBuilder().type("d").build();

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
        assertTrue(TaskConfigRouter.getConfig(router, TASK_SET_A).getAll().isEmpty());
        assertTrue(TaskConfigRouter.getConfig(router, TASK_SET_B).getAll().isEmpty());
        assertTrue(TaskConfigRouter.getConfig(router, TASK_SET_C).getAll().isEmpty());
        assertTrue(TaskConfigRouter.getConfig(router, TASK_SET_D).getAll().isEmpty());
        assertTrue(router.getConfig("E").getAll().isEmpty());
    }

    @Test
    public void testMixedConfig() {
        TaskConfigRouter router = new DefaultTaskConfigRouter(TEST_MAP);
        ImmutableMap<String, ConfigValue> values = TaskConfigRouter.getConfig(router, TASK_SET_A).getAll();
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

        values = TaskConfigRouter.getConfig(router, TASK_SET_B).getAll();
        assertEquals(3, values.size());
        assertEquals("EIGHT", values.get("SEVEN").requiredString());
        assertEquals("TASKCFG_B_SEVEN=EIGHT", values.get("SEVEN").toString());
        assertEquals("BAR", values.get("FOO").requiredString());
        assertEquals("TASKCFG_ALL_FOO=BAR", values.get("FOO").toString());
        assertEquals("BAZ", values.get("BAR").requiredString());
        assertEquals("TASKCFG_ALL_BAR=BAZ", values.get("BAR").toString());

        values = TaskConfigRouter.getConfig(router, TASK_SET_C).getAll();
        assertEquals(3, values.size());
        assertEquals("TEN", values.get("NINE").requiredString());
        assertEquals("TASKCFG_C_NINE=TEN", values.get("NINE").toString());
        assertEquals("BAR", values.get("FOO").requiredString());
        assertEquals("TASKCFG_ALL_FOO=BAR", values.get("FOO").toString());
        assertEquals("BAZ", values.get("BAR").requiredString());
        assertEquals("TASKCFG_ALL_BAR=BAZ", values.get("BAR").toString());

        values = TaskConfigRouter.getConfig(router, TASK_SET_D).getAll();
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
