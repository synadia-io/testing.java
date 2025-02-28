package io.synadia.workloads.support;

import io.nats.client.Options;

public interface SingleThreadedWorker {
    Runnable getWork(Options options, WorkState workState);
}
