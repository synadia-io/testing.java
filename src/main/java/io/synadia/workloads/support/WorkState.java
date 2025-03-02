package io.synadia.workloads.support;

import io.synadia.utils.Commons;

import java.util.concurrent.locks.ReentrantLock;

public class WorkState {
    public final String workId;
    private final boolean[] owed;
    private final ReentrantLock iLock;
    private final ReentrantLock eLock;
    private final ReentrantLock oLock;
    private long groupCount;
    private long elapsed;
    private final long startTime;

    public WorkState(int threadCount) {
        this.workId = Commons.generateWorkId();
        owed = new boolean[threadCount];
        this.iLock = new ReentrantLock();
        this.eLock = new ReentrantLock();
        this.oLock = new ReentrantLock();
        this.groupCount = 0;
        this.startTime = System.currentTimeMillis();
        this.elapsed = 0;
    }

    public void markOthersOwed(int tix) {
        oLock.lock();
        try {
            owed[tix] = false;
            for (int x = 0; x < owed.length; x++) {
                if (x != tix) {
                    owed[x] = true;
                }
            }
        }
        finally {
            oLock.unlock();
        }
    }

    public boolean owed(int tix) {
        oLock.lock();
        try {
            if (owed[tix]) {
                owed[tix] = false;
                return true;
            }
            return false;
        }
        finally {
            oLock.unlock();
        }
    }

    public long get() {
        iLock.lock();
        try {
            return groupCount;
        }
        finally {
            iLock.unlock();
        }
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
        eLock.lock();
        try {
            elapsed = System.currentTimeMillis() - startTime;
        }
        finally {
            eLock.unlock();
        }
        return elapsed;
    }
}
