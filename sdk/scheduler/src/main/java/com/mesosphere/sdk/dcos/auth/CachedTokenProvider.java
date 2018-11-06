package com.mesosphere.sdk.dcos.auth;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.state.CycleDetectingLockUtils;

import com.auth0.jwt.interfaces.DecodedJWT;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * CachedTokenProvider retrieves token from underlying provider and caches the value. It
 * automatically triggers getToken() method on underlying provider when token is about to expire.
 */
public class CachedTokenProvider implements TokenProvider {

  private final TokenProvider provider;

  private final Duration ttl;

  private final Lock rlock;

  private final Lock rwlock;

  private Optional<DecodedJWT> token;

  public CachedTokenProvider(
      TokenProvider provider,
      Duration ttl,
      SchedulerConfig schedulerConfig)
  {
    this.provider = provider;
    this.ttl = ttl;

    ReadWriteLock lock = CycleDetectingLockUtils.newLock(
        schedulerConfig,
        CachedTokenProvider.class);
    this.rlock = lock.readLock();
    this.rwlock = lock.writeLock();

    this.token = Optional.empty();
  }

  @Override
  public DecodedJWT getToken() throws IOException {
    rlock.lock();
    try {
      if (token.isPresent()) {
        Instant triggerRefresh = token.get()
            .getExpiresAt()
            .toInstant()
            .minusSeconds(this.ttl.getSeconds());

        if (triggerRefresh.isAfter(Instant.now())) {
          return token.get();
        }
      }
    } finally {
      rlock.unlock();
    }

    return refreshToken();
  }

  private DecodedJWT refreshToken() throws IOException {
    rwlock.lock();
    try {
      DecodedJWT newToken = provider.getToken();
      token = Optional.of(newToken);
      return newToken;
    } finally {
      rwlock.unlock();
    }
  }
}
