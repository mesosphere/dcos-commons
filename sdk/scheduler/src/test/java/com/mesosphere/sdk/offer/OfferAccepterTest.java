package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
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
    public void testLaunchTransient() {
        Resource resource = ResourceTestUtils.getUnreservedCpus(1.0);
        Offer offer = OfferTestUtils.getCompleteOffer(resource);
        TaskInfo.Builder taskInfoBuilder = TaskTestUtils.getTaskInfo(resource).toBuilder();
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder).setTransient().toProto());

        TestOperationRecorder recorder = new TestOperationRecorder();
        OfferAccepter accepter = new OfferAccepter(Arrays.asList(recorder));
        accepter.accept(
                driver,
                Arrays.asList(new LaunchOfferRecommendation(
                        offer,
                        taskInfoBuilder.build(),
                        Protos.ExecutorInfo.newBuilder().setExecutorId(TestConstants.EXECUTOR_ID).build(),
                        false,
                        true)));
        Assert.assertEquals(1, recorder.getLaunches().size());
        verify(driver, times(0)).acceptOffers(
                anyCollectionOf(OfferID.class),
                anyCollectionOf(Operation.class),
                anyObject());
    }

    @Test
    public void testLaunchTransientCustomExecutor() {
        Resource resource = ResourceTestUtils.getUnreservedCpus(1.0);
        Offer offer = OfferTestUtils.getOffer(resource);
        TaskInfo.Builder taskInfoBuilder = TaskTestUtils.getTaskInfo(resource).toBuilder();
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder).setTransient().toProto());

        TestOperationRecorder recorder = new TestOperationRecorder();
        OfferAccepter accepter = new OfferAccepter(Arrays.asList(recorder));
        accepter.accept(
                driver,
                Arrays.asList(new LaunchOfferRecommendation(
                        offer,
                        taskInfoBuilder.build(),
                        Protos.ExecutorInfo.newBuilder().setExecutorId(TestConstants.EXECUTOR_ID).build(),
                        false,
                        false)));
        Assert.assertEquals(1, recorder.getLaunches().size());
        verify(driver, times(0)).acceptOffers(
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
                case LAUNCH_GROUP:
                    launches.add(operation);
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
