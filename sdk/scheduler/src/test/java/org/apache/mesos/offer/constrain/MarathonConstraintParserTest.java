package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;

/**
 * Tests for {@link MarathonConstraintParser}.
 */
public class MarathonConstraintParserTest {

    @Test
    public void testSplitConstraints() throws IOException {
        assertEquals(Arrays.asList(Arrays.asList("")),
                MarathonConstraintParser.splitConstraints(""));
        assertEquals(Arrays.asList(Arrays.asList("a")),
                MarathonConstraintParser.splitConstraints("a"));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c")),
                MarathonConstraintParser.splitConstraints("['a', 'b', 'c']".replace('\'', '"')));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c")),
                MarathonConstraintParser.splitConstraints("[['a', 'b', 'c']]".replace('\'', '"')));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c"), Arrays.asList("d", "e")),
                MarathonConstraintParser.splitConstraints("[['a', 'b', 'c'], ['d', 'e']]".replace('\'', '"')));
        assertEquals(Arrays.asList(Arrays.asList("a"), Arrays.asList()),
                MarathonConstraintParser.splitConstraints("[['a'], []]".replace('\'', '"')));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c"), Arrays.asList("d", "e")),
                MarathonConstraintParser.splitConstraints("a:b:c,d:e"));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c")),
                MarathonConstraintParser.splitConstraints("a:b:c"));
        assertEquals(Arrays.asList(Arrays.asList("", "", ""), Arrays.asList("", "")),
                MarathonConstraintParser.splitConstraints("::,:"));
    }

    @Test
    public void testParse() {
        assertTrue(false);
    }
}
