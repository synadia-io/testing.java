// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.impl.NoOpStatistics;
import io.synadia.utils.Debug;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TpsStatsCollector extends NoOpStatistics {
    private final AtomicLong bufferedMsgs;
    private final AtomicLong bufferedBytes;
    private final AtomicLong payloadMsgs;
    private final AtomicLong payloadBytes;
    private final AtomicLong afterBufferedMsgs;
    private final AtomicLong afterBufferedBytes;
    private final AtomicLong afterPayloadMsgs;
    private final AtomicLong afterPayloadBytes;
    private final AtomicLong writeBytes;
    private final int payloadSize;
    private final AtomicLong lastPayloadSize;
    private final AtomicLong notWrittenMessages;
    private final AtomicLong notWrittenBytes;
    private final AtomicLong lastWriteMessages;
    private final AtomicLong lastWriteBytes;
    private final AtomicBoolean running;

    public TpsStatsCollector(int payloadSize) {
        bufferedMsgs = new AtomicLong();
        bufferedBytes = new AtomicLong();
        payloadMsgs = new AtomicLong();
        payloadBytes = new AtomicLong();
        afterBufferedMsgs = new AtomicLong();
        afterBufferedBytes = new AtomicLong();
        afterPayloadMsgs = new AtomicLong();
        afterPayloadBytes = new AtomicLong();
        writeBytes = new AtomicLong();
        this.payloadSize = payloadSize;
        lastPayloadSize = new AtomicLong(payloadSize);
        notWrittenMessages = new AtomicLong();
        notWrittenBytes = new AtomicLong();
        lastWriteMessages = new AtomicLong();
        lastWriteBytes = new AtomicLong();
        running = new AtomicBoolean(true);
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void incrementOutBytes(long bytes) {
        if (running.get()) {
            bufferedMsgs.incrementAndGet();
            bufferedBytes.addAndGet(bytes);
            notWrittenMessages.incrementAndGet();
            notWrittenBytes.addAndGet(bytes);
            if (bytes >= payloadSize) {
                payloadMsgs.incrementAndGet();
                payloadBytes.addAndGet(bytes);
                if (bytes > lastPayloadSize.get()) {
                    lastPayloadSize.set(bytes);
                    Debug.info("STATS", "Payload Message Bytes", bytes);
                }
            }
        }
        else {
            afterBufferedMsgs.incrementAndGet();
            afterBufferedBytes.addAndGet(bytes);
            if (bytes >= payloadSize) {
                afterPayloadMsgs.incrementAndGet();
                afterPayloadBytes.addAndGet(bytes);
            }
        }
    }

    @Override
    public void registerWrite(long bytes) {
        if (running.get()) {
            lastWriteMessages.set(notWrittenMessages.get());
            lastWriteBytes.set(notWrittenBytes.get());
            notWrittenMessages.set(0);
            notWrittenBytes.set(0);
            writeBytes.addAndGet(bytes);
        }
    }

    @Override
    public long getOutMsgs() {
        return bufferedMsgs.get();
    }

    @Override
    public long getOutBytes() {
        return bufferedBytes.get();
    }

    public long getPayloadMsgs() {
        return payloadMsgs.get();
    }

    public long getPayloadBytes() {
        return payloadBytes.get();
    }

    public long getWriteBytes() {
        return writeBytes.get();
    }

    public long outDiff() {
        return bufferedBytes.get() - writeBytes.get();
    }

    public long approximateDiffMessages() {
        return outDiff() / lastPayloadSize.get();
    }

    public long getNotWrittenMessages() {
        return notWrittenMessages.get();
    }

    public long getNotWrittenBytes() {
        return notWrittenBytes.get();
    }

    public long getLastWriteMessages() {
        return lastWriteMessages.get();
    }

    public long getLastWriteBytes() {
        return lastWriteBytes.get();
    }

    public long getAfterBufferedMsgs() {
        return afterBufferedMsgs.get();
    }

    public long getAfterBufferedBytes() {
        return afterBufferedBytes.get();
    }

    public long getAfterPayloadMsgs() {
        return afterPayloadMsgs.get();
    }

    public long getAfterPayloadBytes() {
        return afterPayloadBytes.get();
    }
}
