package com.mesosphere.sdk.curator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.ACLPathAndBytesable;
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.curator.framework.api.PathAndBytesable;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionBridge;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.api.transaction.TransactionCheckBuilder;
import org.apache.curator.framework.api.transaction.TransactionCreateBuilder;
import org.apache.curator.framework.api.transaction.TransactionDeleteBuilder;
import org.apache.curator.framework.api.transaction.TransactionSetDataBuilder;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;
import com.mesosphere.sdk.testutils.TestConstants;

import java.nio.charset.Charset;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CuratorPersister}.
 */
public class CuratorPersisterTest {
    @Mock private CuratorFramework mockClient;
    @Mock private CuratorTransaction mockTransaction;
    @Mock private CuratorTransactionFinal mockTransactionFinal;
    @Mock private ExistsBuilder mockExistsBuilder;
    @Mock private Stat mockStat;
    private CuratorPersister mockedPersister;

    @Mock private ServiceSpec mockServiceSpec;
    private static TestingServer testZk;

    // Paths passed to the interface:
    private static final String PATH_PARENT = "/path";
    private static final String PATH_1 = "/path/1";
    private static final String PATH_2 = "/path/2";
    private static final String PATH_SUB_1 = "/path/sub/1";
    private static final String PATH_SUB_2 = "/path/sub/2";

    // Paths accessed internally (where stuff actually goes, prefixed with dcos-service-<svcname>):
    private static final String INTERNAL_PATH_SERVICE = String.format("/dcos-service-%s", TestConstants.SERVICE_NAME);
    private static final String INTERNAL_PATH_PARENT = String.format("/dcos-service-%s/path", TestConstants.SERVICE_NAME);
    private static final String INTERNAL_PATH_1 = String.format("/dcos-service-%s/path/1", TestConstants.SERVICE_NAME);
    private static final String INTERNAL_PATH_2 = String.format("/dcos-service-%s/path/2", TestConstants.SERVICE_NAME);
    private static final String INTERNAL_PATH_SUB_PARENT = String.format("/dcos-service-%s/path/sub", TestConstants.SERVICE_NAME);
    private static final String INTERNAL_PATH_SUB_1 = String.format("/dcos-service-%s/path/sub/1", TestConstants.SERVICE_NAME);
    private static final String INTERNAL_PATH_SUB_2 = String.format("/dcos-service-%s/path/sub/2", TestConstants.SERVICE_NAME);
    private static final List<String> INTERNAL_PATHS = Arrays.asList(
            INTERNAL_PATH_SERVICE, INTERNAL_PATH_PARENT, INTERNAL_PATH_1, INTERNAL_PATH_2, INTERNAL_PATH_SUB_PARENT,
            INTERNAL_PATH_SUB_1, INTERNAL_PATH_SUB_2);

    private static final byte[] DATA_1 = "one".getBytes(Charset.defaultCharset());
    private static final byte[] DATA_2 = "two".getBytes(Charset.defaultCharset());
    private static final byte[] DATA_SUB_1 = "sub_one".getBytes(Charset.defaultCharset());
    private static final byte[] DATA_SUB_2 = "sub_two".getBytes(Charset.defaultCharset());

    private static final Map<String, byte[]> MANY_MAP = new TreeMap<>(); // use consistent ordering
    static {
        MANY_MAP.put(PATH_1, DATA_1);
        MANY_MAP.put(PATH_2, DATA_2);
        MANY_MAP.put(PATH_SUB_1, DATA_SUB_1);
        MANY_MAP.put(PATH_SUB_2, DATA_SUB_2);
    }

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);

        // easier than creating a real ServiceSpec just for two fields (zk connect string is set in tests):
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);

        mockedPersister = new CuratorPersister(TestConstants.SERVICE_NAME, mockClient);
    }

    @Test
    public void testSetManyAgainstEmptySucceeds() throws Exception {
        when(mockClient.checkExists()).thenReturn(mockExistsBuilder);
        for (String path : INTERNAL_PATHS) {
            when(mockExistsBuilder.forPath(path)).thenReturn(null);
        }
        TestTransaction transaction = new TestTransaction(TestTransaction.Result.SUCCESS);
        when(mockClient.inTransaction()).thenReturn(transaction);
        mockedPersister.setMany(MANY_MAP);
        assertEquals(transaction.operations.toString(), 7, transaction.operations.size());
        TestOperation op = transaction.operations.get(0);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_SERVICE, op.path);
        assertNull(op.data);
        op = transaction.operations.get(1);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_PARENT, op.path);
        assertNull(op.data);
        op = transaction.operations.get(2);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_SUB_PARENT, op.path);
        assertNull(op.data);
        op = transaction.operations.get(3);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_1, op.path);
        assertArrayEquals(DATA_1, op.data);
        op = transaction.operations.get(4);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_2, op.path);
        assertArrayEquals(DATA_2, op.data);
        op = transaction.operations.get(5);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_SUB_1, op.path);
        assertArrayEquals(DATA_SUB_1, op.data);
        op = transaction.operations.get(6);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_SUB_2, op.path);
        assertArrayEquals(DATA_SUB_2, op.data);
    }

    @Test
    public void testSetManyAgainstPartialOnesSucceeds() throws Exception {
        when(mockClient.checkExists()).thenReturn(mockExistsBuilder);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SERVICE)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_PARENT)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_1)).thenReturn(null);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_2)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_PARENT)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_1)).thenReturn(null);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_2)).thenReturn(mockStat);
        TestTransaction transaction = new TestTransaction(TestTransaction.Result.SUCCESS);
        when(mockClient.inTransaction()).thenReturn(transaction);
        mockedPersister.setMany(MANY_MAP);
        assertEquals(transaction.operations.toString(), 4, transaction.operations.size());
        TestOperation op = transaction.operations.get(0);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_1, op.path);
        assertArrayEquals(DATA_1, op.data);
        op = transaction.operations.get(1);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_2, op.path);
        assertArrayEquals(DATA_2, op.data);
        op = transaction.operations.get(2);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_SUB_1, op.path);
        assertArrayEquals(DATA_SUB_1, op.data);
        op = transaction.operations.get(3);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_SUB_2, op.path);
        assertArrayEquals(DATA_SUB_2, op.data);
    }

    @Test
    public void testSetManyAgainstPartialRootsSucceeds() throws Exception {
        when(mockClient.checkExists()).thenReturn(mockExistsBuilder);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SERVICE)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_PARENT)).thenReturn(null);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_1)).thenReturn(null);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_2)).thenReturn(null);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_PARENT)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_1)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_2)).thenReturn(mockStat);
        TestTransaction transaction = new TestTransaction(TestTransaction.Result.SUCCESS);
        when(mockClient.inTransaction()).thenReturn(transaction);
        mockedPersister.setMany(MANY_MAP);
        assertEquals(transaction.operations.toString(), 5, transaction.operations.size());
        TestOperation op = transaction.operations.get(0);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_PARENT, op.path);
        assertNull(op.data);
        op = transaction.operations.get(1);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_1, op.path);
        assertArrayEquals(DATA_1, op.data);
        op = transaction.operations.get(2);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_2, op.path);
        assertArrayEquals(DATA_2, op.data);
        op = transaction.operations.get(3);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_SUB_1, op.path);
        assertArrayEquals(DATA_SUB_1, op.data);
        op = transaction.operations.get(4);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_SUB_2, op.path);
        assertArrayEquals(DATA_SUB_2, op.data);
    }

    @Test
    public void testSetManyAgainstPartialSubsSucceeds() throws Exception {
        when(mockClient.checkExists()).thenReturn(mockExistsBuilder);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SERVICE)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_PARENT)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_1)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_2)).thenReturn(mockStat);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_PARENT)).thenReturn(null);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_1)).thenReturn(null);
        when(mockExistsBuilder.forPath(INTERNAL_PATH_SUB_2)).thenReturn(null);
        TestTransaction transaction = new TestTransaction(TestTransaction.Result.SUCCESS);
        when(mockClient.inTransaction()).thenReturn(transaction);
        mockedPersister.setMany(MANY_MAP);
        assertEquals(transaction.operations.toString(), 5, transaction.operations.size());
        TestOperation op = transaction.operations.get(0);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_SUB_PARENT, op.path);
        assertNull(op.data);
        op = transaction.operations.get(1);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_1, op.path);
        assertArrayEquals(DATA_1, op.data);
        op = transaction.operations.get(2);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_2, op.path);
        assertArrayEquals(DATA_2, op.data);
        op = transaction.operations.get(3);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_SUB_1, op.path);
        assertArrayEquals(DATA_SUB_1, op.data);
        op = transaction.operations.get(4);
        assertEquals(TestOperation.Mode.CREATE, op.mode);
        assertEquals(INTERNAL_PATH_SUB_2, op.path);
        assertArrayEquals(DATA_SUB_2, op.data);
    }

    @Test
    public void testSetManyAgainstFullSucceeds() throws Exception {
        when(mockClient.checkExists()).thenReturn(mockExistsBuilder);
        for (String path : INTERNAL_PATHS) {
            when(mockExistsBuilder.forPath(path)).thenReturn(mockStat);
        }
        TestTransaction transaction = new TestTransaction(TestTransaction.Result.SUCCESS);
        when(mockClient.inTransaction()).thenReturn(transaction);
        mockedPersister.setMany(MANY_MAP);
        assertEquals(transaction.operations.toString(), 4, transaction.operations.size());
        TestOperation op = transaction.operations.get(0);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_1, op.path);
        assertArrayEquals(DATA_1, op.data);
        op = transaction.operations.get(1);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_2, op.path);
        assertArrayEquals(DATA_2, op.data);
        op = transaction.operations.get(2);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_SUB_1, op.path);
        assertArrayEquals(DATA_SUB_1, op.data);
        op = transaction.operations.get(3);
        assertEquals(TestOperation.Mode.SET_DATA, op.mode);
        assertEquals(INTERNAL_PATH_SUB_2, op.path);
        assertArrayEquals(DATA_SUB_2, op.data);
    }

    @Test(expected=PersisterException.class)
    public void testSetManyAgainstEmptyFails() throws Exception {
        when(mockClient.checkExists()).thenReturn(mockExistsBuilder);
        for (String path : INTERNAL_PATHS) {
            when(mockExistsBuilder.forPath(path)).thenReturn(null);
        }
        TestTransaction transaction = new TestTransaction(TestTransaction.Result.EXCEPTION);
        when(mockClient.inTransaction()).thenReturn(transaction);
        mockedPersister.setMany(MANY_MAP);
    }

    @Test(expected=PersisterException.class)
    public void testSetManyAgainstFullFails() throws Exception {
        when(mockClient.checkExists()).thenReturn(mockExistsBuilder);
        for (String path : INTERNAL_PATHS) {
            when(mockExistsBuilder.forPath(path)).thenReturn(mockStat);
        }
        TestTransaction transaction = new TestTransaction(TestTransaction.Result.EXCEPTION);
        when(mockClient.inTransaction()).thenReturn(transaction);
        mockedPersister.setMany(MANY_MAP);
    }

    // Uses a real ZK instance to ensure that our integration works as expected:
    @Test
    public void testAclBehavior() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister nonAclPersister = CuratorPersister.newBuilder(mockServiceSpec).build();
        Persister aclPersister = CuratorPersister.newBuilder(mockServiceSpec).setCredentials("testuser", "testpw").build();

        // Store value with ACL.
        aclPersister.set(PATH_1, DATA_1);

        // Readable with appropriate Auth and ACL.
        assertArrayEquals(DATA_1, aclPersister.get(PATH_1));

        // Readable with world:anyone permission.
        assertArrayEquals(DATA_1, nonAclPersister.get(PATH_1));

        // Not overwriteable with world:anyone permission.
        try {
            nonAclPersister.set(PATH_1, DATA_2);
            fail("Should have failed with auth exception");
        } catch (PersisterException e) {
            assertEquals(Reason.STORAGE_ERROR, e.getReason());
            assertTrue(e.getCause() instanceof KeeperException.NoAuthException);
        }

        // Not overwriteable with incorrect Auth
        try {
            Persister wrongAclPersister = CuratorPersister.newBuilder(mockServiceSpec)
                    .setCredentials("testuser", "otherpw").build();
            wrongAclPersister.set(PATH_1, DATA_SUB_1);
            fail("Should have failed with auth exception");
        } catch ( PersisterException e ) {
            assertEquals(Reason.STORAGE_ERROR, e.getReason());
            assertTrue(e.getCause() instanceof KeeperException.NoAuthException);
        }

        // Delete ACL'ed data so that other tests don't have ACL problems trying to clear it:
        aclPersister.deleteAll(PATH_PARENT);
    }

    // Uses a real ZK instance to ensure that our integration works as expected:
    @Test
    public void testDeleteRoot() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).build();

        persister.set("lock", DATA_1);
        persister.set("a", DATA_2);
        persister.set("a/1", DATA_1);
        persister.set("a/lock", DATA_2);
        persister.set("a/2/a", DATA_1);
        persister.set("a/3", DATA_2);
        persister.set("a/3/a/1", DATA_1);
        persister.set("b", DATA_2);
        persister.set("c", DATA_1);
        persister.set("d/1/a/1", DATA_2);

        persister.deleteAll("");

        // The root-level "lock" is preserved:
        assertEquals(Collections.singleton("lock"), persister.getChildren(""));
        assertArrayEquals(DATA_1, persister.get("lock"));
        assertEquals(Collections.singleton("/lock"), PersisterUtils.getAllKeys(persister));
    }

    /**
     * Implements a sort of 'journal' of an operation chain to be performed within a Curator transaction.
     */
    private static class TestTransaction implements CuratorTransactionFinal {

        private final Exception exceptionToThrow;
        private final List<TestOperation> operations = new ArrayList<>();

        private enum Result {
            SUCCESS,
            EXCEPTION
        }

        private static class TestException extends Exception {

        }

        private TestTransaction(Result result) {
            switch (result) {
            case SUCCESS:
                this.exceptionToThrow = null;
                break;
            case EXCEPTION:
                this.exceptionToThrow = new TestException();
                break;
            default:
                throw new IllegalArgumentException("???: " + result);
            }
        }

        @Override
        public TransactionCreateBuilder create() {
            TestCreate operation = new TestCreate(this);
            operations.add(operation);
            return operation;
        }

        @Override
        public TransactionSetDataBuilder setData() {
            TestSetData operation = new TestSetData(this);
            operations.add(operation);
            return operation;
        }

        @Override
        public Collection<CuratorTransactionResult> commit() throws Exception {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return Collections.emptyList();
        }

        @Override
        public TransactionDeleteBuilder delete() {
            throw new UnsupportedOperationException();
        }
        @Override
        public TransactionCheckBuilder check() {
            throw new UnsupportedOperationException();
        }
    }

    private static class TestOperation implements PathAndBytesable<CuratorTransactionBridge> {

        private enum Mode {
            CREATE,
            SET_DATA
        }

        private final CuratorTransactionBridge returnMe;
        private final Mode mode;
        private String path = null;
        private byte[] data = null;

        private TestOperation(Mode mode, CuratorTransactionFinal returnMe) {
            this.mode = mode;
            this.returnMe = new PassthroughBridge(returnMe);
        }

        @Override
        public CuratorTransactionBridge forPath(String path, byte[] data) throws Exception {
            this.path = path;
            this.data = data;
            return returnMe;
        }

        @Override
        public CuratorTransactionBridge forPath(String path) throws Exception {
            this.path = path;
            this.data = null;
            return returnMe;
        }

        @Override
        public String toString() {
            return String.format("%s %s", mode, path);
        }
    }

    private static class TestCreate extends TestOperation implements TransactionCreateBuilder {
        private TestCreate(CuratorTransactionFinal returnMe) {
            super(TestOperation.Mode.CREATE, returnMe);
        }
        @Override
        public ACLPathAndBytesable<CuratorTransactionBridge> withMode(CreateMode mode) {
            throw new UnsupportedOperationException();
        }
        @Override
        public PathAndBytesable<CuratorTransactionBridge> withACL(List<ACL> aclList) {
            throw new UnsupportedOperationException();
        }
        @Override
        public ACLPathAndBytesable<CuratorTransactionBridge> compressed() {
            throw new UnsupportedOperationException();
        }
    }

    private static class TestSetData extends TestOperation implements TransactionSetDataBuilder {
        private TestSetData(CuratorTransactionFinal returnMe) {
            super(TestOperation.Mode.SET_DATA, returnMe);
        }
        @Override
        public PathAndBytesable<CuratorTransactionBridge> withVersion(int version) {
            throw new UnsupportedOperationException();
        }
        @Override
        public PathAndBytesable<CuratorTransactionBridge> compressed() {
            throw new UnsupportedOperationException();
        }
    }

    private static class PassthroughBridge implements CuratorTransactionBridge {
        private final CuratorTransactionFinal transaction;
        public PassthroughBridge(CuratorTransactionFinal transaction) {
            this.transaction = transaction;
        }
        public CuratorTransactionFinal and() {
            return transaction;
        }
    }
}
