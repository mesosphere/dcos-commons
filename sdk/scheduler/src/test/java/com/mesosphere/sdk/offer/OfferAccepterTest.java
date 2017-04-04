package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.OfferTestUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class OfferAccepterTest {

    @Mock
    private SchedulerDriver driver;

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testConstructor() {
        OfferAccepter accepter = new OfferAccepter(new TestOperationRecorder());
        Assert.assertNotNull(accepter);
    }

    @Test
    public void testLaunchTransient() {
        Resource resource = ResourceTestUtils.getUnreservedCpu(1.0);
        Offer offer = OfferTestUtils.getOffer(resource);
        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(resource);
        taskInfo = CommonTaskUtils.setTransient(taskInfo.toBuilder()).build();

        TestOperationRecorder recorder = new TestOperationRecorder();
        OfferAccepter accepter = new OfferAccepter(recorder);
        accepter.accept(driver, Arrays.asList(new LaunchOfferRecommendation(offer, taskInfo)));
        Assert.assertEquals(1, recorder.getLaunches().size());
        verify(driver, times(0)).acceptOffers(
                anyCollectionOf(OfferID.class),
                anyCollectionOf(Operation.class),
                anyObject());
    }

    @Test
    public void testClearTransient() {
        Resource resource = ResourceTestUtils.getUnreservedCpu(1.0);
        Offer offer = OfferTestUtils.getOffer(resource);
        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(resource);
        taskInfo = CommonTaskUtils.setTransient(taskInfo.toBuilder()).build();

        TestOperationRecorder recorder = new TestOperationRecorder();
        OfferAccepter accepter = new OfferAccepter(recorder);
        accepter.accept(driver, Arrays.asList(new LaunchOfferRecommendation(offer, taskInfo)));
        Assert.assertEquals(1, recorder.getLaunches().size());
        verify(driver, times(0)).acceptOffers(
                anyCollectionOf(OfferID.class),
                anyCollectionOf(Operation.class),
                anyObject());

        taskInfo = CommonTaskUtils.clearTransient(taskInfo.toBuilder()).build();
        accepter.accept(driver, Arrays.asList(new LaunchOfferRecommendation(offer, taskInfo)));
        Assert.assertEquals(2, recorder.getLaunches().size());
        verify(driver, times(1)).acceptOffers(
                anyCollectionOf(OfferID.class),
                anyCollectionOf(Operation.class),
                anyObject());
    }

    public static class TestOperationRecorder implements OperationRecorder {
        private List<Operation> reserves = new ArrayList<>();
        private List<Operation> unreserves = new ArrayList<>();
        private List<Operation> creates = new ArrayList<>();
        private List<Operation> destroys = new ArrayList<>();
        private List<Operation> launches = new ArrayList<>();

        public void record(OfferRecommendation offerRecommendation) throws Exception {
            Operation operation = offerRecommendation.getOperation();
            switch (operation.getType()) {
                case UNRESERVE:
                    unreserves.add(operation);
                    break;
                case RESERVE:
                    reserves.add(operation);
                    break;
                case CREATE:
                    creates.add(operation);
                    break;
                case DESTROY:
                    destroys.add(operation);
                    break;
                case LAUNCH:
                    launches.add(operation);
                    break;
                default:
                    throw new Exception("Unknown operation type encountered");
            }
        }

        public List<Operation> getReserves() {
            return reserves;
        }

        public List<Operation> getUnreserves() {
            return unreserves;
        }

        public List<Operation> getCreates() {
            return creates;
        }

        public List<Operation> getDestroys() {
            return destroys;
        }

        public List<Operation> getLaunches() {
            return launches;
        }
    }
}
