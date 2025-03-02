package io.synadia.workloads.support;

import io.synadia.utils.Commons;

import java.util.concurrent.locks.ReentrantLock;

public class WorkState {
    public final String workId;
    private final int[] owedLogsCount;
    private final ReentrantLock iLock;
    private final ReentrantLock eLock;
    private final ReentrantLock lLock;
    private long groupCount;
    private long elapsed;
    private final long startTime;

    public WorkState(int threadCount) {
        this.workId = Commons.generateWorkId();
        owedLogsCount = new int[threadCount];
        this.iLock = new ReentrantLock();
        this.eLock = new ReentrantLock();
        this.lLock = new ReentrantLock();
        this.groupCount = 0;
        this.startTime = System.currentTimeMillis();
        this.elapsed = 0;
    }

    public void markGroupAsLogged() {
        lLock.lock();
        try {
            for (int x = 0; x < owedLogsCount.length; x++) {
                owedLogsCount[x]++;
            }
        }
        finally {
            lLock.unlock();
        }
    }

    public boolean shouldLogOwn(int tix) {
        lLock.lock();
        try {
            boolean should = owedLogsCount[tix] > 0;
            if (--owedLogsCount[tix] < 1) {
                owedLogsCount[tix] = 0;
            }
            return should;
        }
        finally {
            lLock.unlock();
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
