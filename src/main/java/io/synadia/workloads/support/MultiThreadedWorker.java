package io.synadia.workloads.support;

import io.nats.client.Options;

public interface MultiThreadedWorker {
    Runnable getWork(Options options, int tix, WorkState workState);
}
