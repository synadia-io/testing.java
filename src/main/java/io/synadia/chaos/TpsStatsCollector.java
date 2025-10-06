// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.impl.NoOpStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TpsStatsCollector extends NoOpStatistics {
    public static class TimeTrack {
        long time;
        long value;

        public TimeTrack(long value) {
            time = System.currentTimeMillis();
            this.value = value;
        }
    }

    private final AtomicLong outMsgs;
    private final AtomicLong outBytes;
    private final AtomicLong writeBytes;

    private final List<TimeTrack> outMsgsList;
    private final List<TimeTrack> outBytesList;
    private final List<TimeTrack> writeBytesList;

    public TpsStatsCollector() {
        outMsgs = new AtomicLong();
        outBytes = new AtomicLong();
        writeBytes = new AtomicLong();
        outMsgsList = new ArrayList<>();
        outBytesList = new ArrayList<>();
        writeBytesList = new ArrayList<>();
    }

    @Override
    public void setAdvancedTracking(boolean trackAdvanced) {}

    @Override
    public void incrementOutMsgs() {
        outMsgsList.add(new TimeTrack(outMsgs.incrementAndGet()));
    }

    @Override
    public void incrementOutBytes(long bytes) {
        outBytesList.add(new TimeTrack(outBytes.addAndGet(bytes)));
    }

    @Override
    public void registerWrite(long bytes) {
        writeBytesList.add(new TimeTrack(writeBytes.addAndGet(bytes)));
    }

    @Override
    public long getOutMsgs() {
        return outMsgs.get();
    }

    @Override
    public long getOutBytes() {
        return outBytes.get();
    }

    public long getWriteBytes() {
        return writeBytes.get();
    }

    public List<TimeTrack> getOutMsgsList() {
        return outMsgsList;
    }

    public List<TimeTrack> getOutBytesList() {
        return outBytesList;
    }

    public List<TimeTrack> getWriteBytesList() {
        return writeBytesList;
    }
}
