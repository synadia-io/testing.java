package io.synadia.workloads.support;

import io.synadia.utils.Commons;

import java.util.concurrent.locks.ReentrantLock;

public class WorkState {
    public final String workId;
    private final ReentrantLock iLock;
    private final ReentrantLock uLock;
    private long groupCount;
    private long elapsed;
    private final long startTime;

    public WorkState() {
        this.workId = Commons.generateWorkId();
        this.iLock = new ReentrantLock();
        this.uLock = new ReentrantLock();
        this.groupCount = 0;
        this.startTime = System.currentTimeMillis();
        this.elapsed = 0;
    }

    public long increment() {
        iLock.lock();
        try {
            return ++groupCount;
        }
        finally {
            iLock.unlock();
        }
    }

    public long elapse() {
        uLock.lock();
        try {
            elapsed = System.currentTimeMillis() - startTime;
        }
        finally {
            uLock.unlock();
        }
        return elapsed;
    }

}
