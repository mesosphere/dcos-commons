package com.mesosphere.sdk.scheduler.multi;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.PersisterException;

/**
 * Tests for {@link ParallelFootprintDiscipline}
 */
public class ParallelFootprintDisciplineTest {

    private DisciplineSelectionStore store;

    @Before
    public void beforeEach() throws PersisterException {
        store = new DisciplineSelectionStore(new MemPersister());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testZeroLimit() {
        new ParallelFootprintDiscipline(0, store);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNegativeLimit() {
        new ParallelFootprintDiscipline(-1, store);
    }

    @Test(expected=IllegalStateException.class)
    public void testMissingUpdate() {
        OfferDiscipline d = new ParallelFootprintDiscipline(1, store);
        d.updateServiceStatus("1", ClientStatusResponse.launching(false));
    }

    @Test
    public void testNotReservingStates() throws Exception {
        OfferDiscipline d = new ParallelFootprintDiscipline(1, store);
        d.updateServices(Arrays.asList("1", "2", "3"));

        // 1 takes slot with reserving:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));

        // 1 gives up slot with readyToUninstall, 2 takes it:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.readyToUninstall()));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));

        // 2 gives up slot with launching, 1 takes it:
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.launching(false)));
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));

        // 1 gives up slot with readyToRemove, 2 takes it:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.readyToRemove()));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
    }

    @Test
    public void testSingleSlot() throws Exception {
        OfferDiscipline d = new ParallelFootprintDiscipline(1, store);
        d.updateServices(Arrays.asList("1", "2", "3"));
        Assert.assertEquals(Collections.emptySet(), store.fetchSelectedServices());

        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.launching(false)));
        // 2 can reserve (gets the slot):
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        // 3 cannot reserve:
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
        // .. but 3 can run:
        Assert.assertTrue(d.updateServiceStatus("3", ClientStatusResponse.launching(false)));
        // Meanwhile 2 can still reserve:
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));

        // 2 gets removed:
        d.updateServices(Arrays.asList("1", "3"));
        // Now 3 can reserve (gets the slot):
        Assert.assertTrue(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
        // And now 1 cannot reserve:
        Assert.assertFalse(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        // 3 can still reserve:
        Assert.assertTrue(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
    }

    @Test
    public void testSingleSlotStartsOccupied() throws Exception {
        OfferDiscipline d = new ParallelFootprintDiscipline(1, store);

        // Start with 2 selected:
        store.storeSelectedServices(Collections.singleton("2"));
        d.updateServices(Arrays.asList("1", "2", "3"));
        // Update state (would internally be a no-op due to no change):
        Assert.assertEquals(Collections.singleton("2"), store.fetchSelectedServices());

        // Only 2 can reserve:
        Assert.assertFalse(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));

        // After 2 has stopped reserving, 1 can reserve:
        Assert.assertFalse(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.launching(false)));
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
    }

    @Test
    public void testSingleSlotUnknownStored() throws Exception {
        OfferDiscipline d = new ParallelFootprintDiscipline(1, store);

        // Store has 2 and 3, but active set is 1 and 2. Only 2 keeps its slot:
        store.storeSelectedServices(new HashSet<>(Arrays.asList("2", "3")));
        d.updateServices(Arrays.asList("1", "2"));
        // Pruned to reflect remaining slot:
        Assert.assertEquals(Collections.singleton("2"), store.fetchSelectedServices());

        // Now only 2 gets to keep its slot:
        Assert.assertFalse(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));

        // sanity check: 1 gets slot after 2 gives it up:
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.launching(false)));
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
    }

    @Test
    public void testMultiSlot() throws Exception {
        OfferDiscipline d = new ParallelFootprintDiscipline(2, store);
        d.updateServices(Arrays.asList("1", "2", "3"));
        // Store initial empty state:
        Assert.assertEquals(Collections.emptySet(), store.fetchSelectedServices());

        // 1 and 2 can reserve (get slots):
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        // 3 cannot reserve, but 1 and 2 can:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
        // After 1 gives up its slot, 3 gets it:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.launching(false)));
        Assert.assertTrue(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));

        // 2 gets removed:
        d.updateServices(Arrays.asList("1", "3"));
        // Change is stored with 2's slot revoked:
        Assert.assertEquals(Collections.singleton("3"), store.fetchSelectedServices());
        // Now 1 can reserve (gets the slot), and 3 can still reserve:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
    }

    @Test
    public void testMultiSlotStartsOccupied() throws Exception {
        OfferDiscipline d = new ParallelFootprintDiscipline(2, store);

        // Start with 2 selected:
        store.storeSelectedServices(Collections.singleton("2"));
        d.updateServices(Arrays.asList("1", "2", "3"));
        // Store initial selection state (would internally be a no-op):
        Assert.assertEquals(Collections.singleton("2"), store.fetchSelectedServices());

        // 2 can already reserve, and 1 gets the remaining slot:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));

        // After 2 has stopped reserving, 3 can reserve:
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.launching(false)));
        Assert.assertTrue(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
    }

    @Test
    public void testMultiSlotUnknownStored() throws Exception {
        OfferDiscipline d = new ParallelFootprintDiscipline(2, store);

        // Store has 2 and 3, but active set is 1 and 2.
        store.storeSelectedServices(new HashSet<>(Arrays.asList("2", "3")));
        d.updateServices(Arrays.asList("1", "2"));
        // Pruned to reflect active set:
        Assert.assertEquals(Collections.singleton("2"), store.fetchSelectedServices());

        // 1 and 2 both get slots due to limit=2:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
    }

    @Test
    public void testSlotIncreaseDecrease() throws Exception {
        OfferDiscipline d = new ParallelFootprintDiscipline(1, store);
        d.updateServices(Arrays.asList("1", "2", "3"));
        // Store initial empty state:
        Assert.assertEquals(Collections.emptySet(), store.fetchSelectedServices());

        // 2 gets the slot:
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));

        // Trigger a persister update:
        d.updateServices(Arrays.asList("1", "2", "3"));
        Assert.assertEquals(Collections.singleton("2"), store.fetchSelectedServices());

        // Now bump to 2 slots:
        d = new ParallelFootprintDiscipline(2, store);
        // Store initial selection state (would internally be a no-op):
        d.updateServices(Arrays.asList("1", "2", "3"));
        Assert.assertEquals(Collections.singleton("2"), store.fetchSelectedServices());

        // 2 should already have a slot, leading to 3 getting blocked:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));

        // Trigger another persister update:
        d.updateServices(Arrays.asList("1", "2", "3"));
        Assert.assertEquals(new HashSet<>(Arrays.asList("1", "2")), store.fetchSelectedServices());

        // Reduce back to 1 slot:
        d = new ParallelFootprintDiscipline(1, store);
        d.updateServices(Arrays.asList("1", "2", "3"));
        Assert.assertEquals(new HashSet<>(Arrays.asList("1", "2")), store.fetchSelectedServices());

        // Retain the existing 2 selected services, even though the limit is 1:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.footprint(false)));
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));

        // Updating storage retains them too:
        d.updateServices(Arrays.asList("1", "2", "3"));
        Assert.assertEquals(new HashSet<>(Arrays.asList("1", "2")), store.fetchSelectedServices());

        // 2 stops reserving, slot is freed:
        Assert.assertTrue(d.updateServiceStatus("2", ClientStatusResponse.launching(false)));
        // 3 can't get the slot due to the slot decrease:
        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
        // But 1 can still reserve:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.footprint(false)));

        Assert.assertFalse(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));
        // 1 stops reserving:
        Assert.assertTrue(d.updateServiceStatus("1", ClientStatusResponse.launching(false)));
        // NOW 3 can reserve:
        Assert.assertTrue(d.updateServiceStatus("3", ClientStatusResponse.footprint(false)));

        // Update storage once more:
        d.updateServices(Arrays.asList("1", "2", "3"));
        Assert.assertEquals(new HashSet<>(Arrays.asList("3")), store.fetchSelectedServices());
    }
}
