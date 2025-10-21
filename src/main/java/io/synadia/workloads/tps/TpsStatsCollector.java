// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.impl.NoOpStatistics;
import io.synadia.utils.Debug;

import java.util.concurrent.atomic.AtomicBoolean;

public class TpsStatsCollector extends NoOpStatistics {
    public static class Collector {
        public long bufferedMessages = 0;
        public long bufferedBytes = 0;
        public long writtenMessages = 0;
        public long writtenBytes = 0;
        public long notWrittenMessages = 0;
        public long notWrittenBytes = 0;
    }

    public final Collector payloadCollector = new Collector();
    public final Collector protocolCollector = new Collector();

    public final Collector payloadCollector2 = new Collector();
    public final Collector protocolCollector2 = new Collector();

    public long lastWriteMessages = 0;
    public long lastWriteBytes = 0;

    public final int payloadSize;
    public final AtomicBoolean phase1;

    public TpsStatsCollector(int payloadSize) {
        this.payloadSize = payloadSize;
        phase1 = new AtomicBoolean(true);
    }

    public void startPhase2() {
        phase1.set(false);
    }

    @Override
    public void incrementOutBytes(long bytes) {
        Collector c;
        if (phase1.get()) {
            if (bytes >= payloadSize) {
                c = payloadCollector;
            }
            else {
                c = protocolCollector;
            }
        }
        else if (bytes >= payloadSize) {
            c = payloadCollector2;
        }
        else {
            c = protocolCollector2;
        }
        c.bufferedMessages++;
        c.bufferedBytes += bytes;
        c.notWrittenMessages++;
        c.notWrittenBytes += bytes;
    }

    @Override
    public void registerWrite(long bytes) {
        if (phase1.get()) {

            payloadCollector.writtenMessages += payloadCollector.notWrittenMessages;
            protocolCollector.writtenMessages += protocolCollector.notWrittenMessages;
            payloadCollector.writtenBytes += payloadCollector.notWrittenBytes;
            protocolCollector.writtenBytes += protocolCollector.notWrittenBytes;

            long notWrittenMessages = payloadCollector.notWrittenMessages + protocolCollector.notWrittenMessages;
            long notWrittenBytes = payloadCollector.notWrittenBytes + protocolCollector.notWrittenBytes;
            if (notWrittenBytes != bytes) {
                Debug.info("STATS", "Mismatch %s vs %s", notWrittenBytes, bytes);
            }

            lastWriteMessages = notWrittenMessages;
            lastWriteBytes = bytes;

            payloadCollector.notWrittenMessages = 0;
            protocolCollector.notWrittenMessages = 0;
            payloadCollector.notWrittenBytes = 0;
            protocolCollector.notWrittenBytes = 0;
        }
    }
}
