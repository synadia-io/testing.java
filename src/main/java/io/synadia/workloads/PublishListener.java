// Copyright (c) 2024 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads;

import io.synadia.Debug;
import io.synadia.jnats.extension.AsyncJsPublishListener;
import io.synadia.jnats.extension.InFlight;
import io.synadia.jnats.extension.PostFlight;

import java.util.concurrent.atomic.AtomicLong;

public class PublishListener implements AsyncJsPublishListener {
    public AtomicLong published = new AtomicLong();
    public AtomicLong acked = new AtomicLong();
    public AtomicLong exceptioned = new AtomicLong();
    public AtomicLong timedOut = new AtomicLong();
    public AtomicLong start = new AtomicLong();

    @Override
    public void published(InFlight flight) {
        start.compareAndSet(0, System.currentTimeMillis());
        published.incrementAndGet();
    }

    public long elapsed() {
        return System.currentTimeMillis() - start.get();
    }

    @Override
    public void acked(PostFlight postFlight) {
        acked.incrementAndGet();
    }

    @Override
    public void completedExceptionally(PostFlight postFlight) {
        exceptioned.incrementAndGet();
        if (postFlight.expectationFailed) {
            Debug.info("PL", "Expectation Failed", new String(postFlight.getBody()), postFlight.cause);
        }
        else {
            Debug.info("PL", "Completed Exceptionally", new String(postFlight.getBody()), postFlight.cause);
        }
    }

    @Override
    public void timeout(PostFlight postFlight) {
        timedOut.incrementAndGet();
        Debug.info("PL", "Timed-out", new String(postFlight.getBody()) );
    }
}
