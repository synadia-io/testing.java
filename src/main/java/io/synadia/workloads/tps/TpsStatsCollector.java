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

    public final Collector pay = new Collector();
    public final Collector non = new Collector();

    public final Collector pay2 = new Collector();
    public final Collector non2 = new Collector();

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
                c = pay;
            }
            else {
                c = non;
            }
        }
        else if (bytes >= payloadSize) {
            c = pay2;
        }
        else {
            c = non2;
        }
        c.bufferedMessages++;
        c.bufferedBytes += bytes;
        c.notWrittenMessages++;
        c.notWrittenBytes += bytes;
    }

    @Override
    public void registerWrite(long bytes) {

        Collector cPay;
        Collector cNon;
        if (phase1.get()) {
            cPay = pay;
            cNon = non;
        }
        else {
            cPay = pay2;
            cNon = non2;
        }

        if (phase1.get()) {
            long notWrittenBytes = cPay.notWrittenBytes + cNon.notWrittenBytes;
            if (notWrittenBytes > 0 && notWrittenBytes != bytes) {
                Debug.info("STATS", "MISMATCH %s vs %s", notWrittenBytes, bytes);
            }
            lastWriteMessages = cPay.notWrittenMessages + cNon.notWrittenMessages;
            lastWriteBytes = bytes;
        }

        cPay.writtenMessages += cPay.notWrittenMessages;
        cNon.writtenMessages += cNon.notWrittenMessages;
        cPay.writtenBytes += cPay.notWrittenBytes;
        cNon.writtenBytes += cNon.notWrittenBytes;
        cPay.notWrittenMessages = 0;
        cNon.notWrittenMessages = 0;
        cPay.notWrittenBytes = 0;
        cNon.notWrittenBytes = 0;
    }
}
