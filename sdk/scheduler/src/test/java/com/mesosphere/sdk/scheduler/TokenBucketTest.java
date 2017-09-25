package com.mesosphere.sdk.scheduler;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;

/**
 * This class tests the {@link TokenBucket}.
 */
public class TokenBucketTest {
    @Test
    public void acquireFailsOnEmptyBucket() {
        TokenBucket bucket = TokenBucket.newBuilder()
                .initialTokenCount(0)
                .build();
        Assert.assertFalse(bucket.tryAcquire());
    }

    @Test
    public void acquireSucceedsOnSingleTokenBucker() {
        TokenBucket bucket = TokenBucket.newBuilder()
                .initialTokenCount(1)
                .build();
        Assert.assertTrue(bucket.tryAcquire());
    }

    @Test
    public void acquireFailsOnTooFastAcquire() {
        TokenBucket bucket = TokenBucket.newBuilder()
                .initialTokenCount(2)
                .build();
        Assert.assertTrue(bucket.tryAcquire());
        Assert.assertFalse(bucket.tryAcquire());
    }

    @Test
    public void allowSuperFastAcquire() {
        TokenBucket bucket = TokenBucket.newBuilder()
                .initialTokenCount(2)
                .acquireInterval(Duration.ZERO)
                .build();
        Assert.assertTrue(bucket.tryAcquire());
        Assert.assertTrue(bucket.tryAcquire());
    }

    @Test
    public void exhaustTokens() {
        TokenBucket bucket = TokenBucket.newBuilder()
                .initialTokenCount(1)
                .acquireInterval(Duration.ZERO)
                .build();
        Assert.assertTrue(bucket.tryAcquire());
        Assert.assertFalse(bucket.tryAcquire());
    }

    @Test
    public void replenishTokens() throws InterruptedException {
        Duration incrementInterval = Duration.ofMillis(100);
        TokenBucket bucket = TokenBucket.newBuilder()
                .initialTokenCount(1)
                .acquireInterval(Duration.ZERO)
                .incrementInterval(incrementInterval)
                .build();

        Assert.assertTrue(bucket.tryAcquire());
        Assert.assertFalse(bucket.tryAcquire());
        Thread.sleep(incrementInterval.toMillis() + 50); // Give 50ms of breathing room to the token incrementing thread
        Assert.assertTrue(bucket.tryAcquire());
    }

    @Test
    public void acquireIntervalEnforced() throws InterruptedException {
        Duration acquireInterval = Duration.ofMillis(100);
        TestTokenBucket bucket = new TestTokenBucket(
                TokenBucket.newBuilder()
                .initialTokenCount(2)
                .acquireInterval(acquireInterval));

        Assert.assertTrue(bucket.tryAcquire());
        Assert.assertFalse(bucket.tryAcquire());
        bucket.increment(acquireInterval.toMillis());
        Assert.assertTrue(bucket.tryAcquire());
    }

    @Test(expected = IllegalStateException.class)
    public void invalidCapacity() {
        TokenBucket.newBuilder()
                .capacity(0)
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void invalidTokenCount() {
        TokenBucket.newBuilder()
                .initialTokenCount(-1)
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void invalidIncrementIntervalZero() {
        TokenBucket.newBuilder()
                .incrementInterval(Duration.ZERO)
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void invalidIncrementIntervalNegative() {
        TokenBucket.newBuilder()
                .incrementInterval(Duration.ofSeconds(-1))
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void invalidAcquireInterval() {
        TokenBucket.newBuilder()
                .acquireInterval(Duration.ofSeconds(-1))
                .build();
    }

    private static class TestTokenBucket extends TokenBucket {
        private long now = System.currentTimeMillis();

        private TestTokenBucket(Builder builder) {
            super(builder.initial, builder.capacity, builder.incrementInterval, builder.acquireInterval);
        }

        public void increment(long milliseconds) {
            now += milliseconds;
        }

        @Override
        protected long now() {
            return now;
        }
    }
}
